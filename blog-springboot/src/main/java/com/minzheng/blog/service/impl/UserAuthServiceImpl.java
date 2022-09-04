package com.minzheng.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.constant.CommonConst;
import com.minzheng.blog.dao.UserAuthDao;
import com.minzheng.blog.dao.UserInfoDao;
import com.minzheng.blog.dao.UserRoleDao;
import com.minzheng.blog.dto.EmailDTO;
import com.minzheng.blog.dto.UserAreaDTO;
import com.minzheng.blog.dto.UserBackDTO;
import com.minzheng.blog.dto.UserInfoDTO;
import com.minzheng.blog.entity.UserAuth;
import com.minzheng.blog.entity.UserInfo;
import com.minzheng.blog.entity.UserRole;
import com.minzheng.blog.enums.LoginTypeEnum;
import com.minzheng.blog.enums.RoleEnum;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.BlogInfoService;
import com.minzheng.blog.service.RedisService;
import com.minzheng.blog.service.UserAuthService;
import com.minzheng.blog.strategy.context.SocialLoginStrategyContext;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.util.UserUtils;
import com.minzheng.blog.vo.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.*;
import static com.minzheng.blog.constant.MQPrefixConst.EMAIL_EXCHANGE;
import static com.minzheng.blog.constant.RedisPrefixConst.*;
import static com.minzheng.blog.enums.UserAreaTypeEnum.getUserAreaType;
import static com.minzheng.blog.util.CommonUtils.checkEmail;
import static com.minzheng.blog.util.CommonUtils.getRandomCode;


/**
 * 用户账号服务
 *
 */
@Service
public class UserAuthServiceImpl extends ServiceImpl<UserAuthDao, UserAuth> implements UserAuthService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private UserAuthDao userAuthDao;
    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private UserInfoDao userInfoDao;
    @Autowired
    private BlogInfoService blogInfoService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private SocialLoginStrategyContext socialLoginStrategyContext;

    /**
     * 发送邮箱验证码
     *
     * @param username 邮箱号
     */
    @Override
    public void sendCode(String username) {
        // 校验账号是否合法
        if (!checkEmail(username)) {
            throw new BizException("邮箱格式不正确，请输入正确邮箱");
        }
        //调用工具类生成随机验证码
        String code = getRandomCode();
        //创建邮件对象，发送内容，验证码，邮箱号
        EmailDTO emailDTO = EmailDTO.builder()
            .content("您的验证码为 " + code + " 有效期15分钟，请不要告诉他人哦！")
            .subject("注册验证码")
            .email(username)
            .build();
        //todo rabbit mq 发送验证码
        rabbitTemplate.convertAndSend(EMAIL_EXCHANGE, "*", new Message(JSON.toJSONBytes(emailDTO), new MessageProperties()));
        // todo redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);将验证码存入redis，设置过期时间为15分钟,CODE_EXPIRE_TIME = 15 * 60
        redisService.set(USER_CODE_KEY + username, code, CODE_EXPIRE_TIME);

    }


    /**
     * 校验用户数据是否合法
     *
     * @param user 用户数据
     * @return 结果
     */
    private Boolean checkUser(UserVO user) {
        //todo 从redis中获取邮箱验证码，redisTemplate.opsForValue().get(key);
        String redisCode = (String) redisService.get(USER_CODE_KEY + user.getUsername());
        //如果传入的验证码和redis获取的不一样，抛出异常
        if (!user.getCode().equals(redisCode)) {
            throw new BizException("输入的验证码错误哦！请重新尝试");
        }
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper
            .select(UserAuth::getUsername)
            .eq(UserAuth::getUsername, user.getUsername());
        UserAuth userAuth = userAuthDao.selectOne(wrapper);
        //验证码正确，从数据库中查询是否有该用户，如果该用户存在说明邮箱已经被注册了
        if (userAuth != null) {
            return true;
        }
        return false;
    }

    /**
     * 用户注册
     * 跟用户相关的3张表插入数据：
     * userinfo用户信息表：邮箱，昵称，头像，简介
     * userauth用户账号表：用户名/邮箱、密码，userinfo id
     * userrole用户角色表：userid，role id
     *
     * @param user 用户对象
     */
    @Override
    public void register(UserVO user) {
        //如果该用户存在说明邮箱已经被注册了
        if (checkUser(user)) {
            throw new BizException("邮箱已经被注册啦！");
        }
        //用户信息表
        UserInfo userInfo = UserInfo.builder()
            //随机id作为默认昵称
            .nickname(CommonConst.DEFAULT_NICKNAME + IdWorker.getId())
            .email(user.getUsername())
            //默认头像
            .avatar(blogInfoService.getWebsiteConfig().getUserAvatar())
            .build();
        userInfoDao.insert(userInfo);
        //用户账号表
        UserAuth userAuth = UserAuth.builder()
            .userInfoId(userInfo.getId())
            .username(user.getUsername())
            //todo 插入到数据库的密码需要加密
            .password(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()))
            //登录方式，1邮箱，2qq，3微博
            .loginType(LoginTypeEnum.EMAIL.getType())
            .build();
        userAuthDao.insert(userAuth);
        //绑定用户角色表，默认普通用户，没有权限进入后台
        UserRole userRole = UserRole.builder()
            .userId(userInfo.getId())
            //2普通用户，1管理员，3测试
            .roleId(RoleEnum.USER.getRoleId())
            .build();
        userRoleDao.insert(userRole);
    }

    /**
     * qq登录
     *
     * @param qqLoginVO qq登录信息
     * @return 用户登录信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserInfoDTO qqLogin(QQLoginVO qqLoginVO) {
        //todo 策略模式，qq登录2
        UserInfoDTO userInfoDTO = socialLoginStrategyContext.executeLoginStrategy(JSON.toJSONString(qqLoginVO), LoginTypeEnum.QQ);
        return userInfoDTO;
    }

    /**
     * 微博登录
     *
     * @param weiboLoginVO 微博登录信息
     * @return 用户登录信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserInfoDTO weiboLogin(WeiboLoginVO weiboLoginVO) {
        //todo 策略模式，3微博登录
        UserInfoDTO userInfoDTO = socialLoginStrategyContext.executeLoginStrategy(JSON.toJSONString(weiboLoginVO), LoginTypeEnum.WEIBO);
        return userInfoDTO;
    }


    /**
     * 码云登录
     * @param giteeLoginVO 码云登录信息
     * @return 用户登录信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserInfoDTO giteeLogin(GiteeLoginVO giteeLoginVO) {
        //todo 策略模式，4码云登录
        UserInfoDTO userInfoDTO = socialLoginStrategyContext.executeLoginStrategy(JSON.toJSONString(giteeLoginVO), LoginTypeEnum.GITEE);
        return userInfoDTO;
    }

    /**
     * 修改用户密码
     *
     * @param user 用户对象
     */
    @Override
    public void updatePassword(UserVO user) {
        //数据库找不到该用户信息--》没有注册不能修改密码
        if (!checkUser(user)) {

            throw new BizException("邮箱还没注册呢！");
        }
        //根据用户名修改密码
        LambdaUpdateWrapper<UserAuth> wrapper = new LambdaUpdateWrapper<>();
        wrapper
            //新密码加密
            .set(UserAuth::getPassword, BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()))
            .eq(UserAuth::getUsername, user.getUsername());
        userAuthDao.update(new UserAuth(), wrapper);
    }

    /**
     * 修改管理员密码
     *
     * @param passwordVO 密码对象
     */
    @Override
    public void updateAdminPassword(PasswordVO passwordVO) {
        // 查询旧密码是否正确
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAuth::getId, UserUtils.getLoginUser().getId());
        UserAuth userAuth = userAuthDao.selectOne(wrapper);
        //将输入的密码和数据库中解密的密码比对正确
        if (userAuth != null && BCrypt.checkpw(passwordVO.getNewPassword(), userAuth.getPassword())) {

            UserAuth user = UserAuth.builder()
                .id(UserUtils.getLoginUser().getId())
                //将新密码重新加密
                .password(BCrypt.hashpw(passwordVO.getNewPassword(), BCrypt.gensalt()))
                .build();
            userAuthDao.updateById(user);
        } else {
            throw new BizException("原密码不正确！请重新输入");
        }
    }

    /**
     * 查询后台用户列表
     *
     * @param condition 条件
     * @return 用户列表
     */
    @Override
    public PageResult<UserBackDTO> listUserBackDTO(ConditionVO condition) {
        //查询后台用户数量
        Integer countUser = userAuthDao.countUser(condition);
        if (countUser == 0) {
            return new PageResult<>();
        }
        // 获取后台用户列表
        List<UserBackDTO> userBackDTOList = userAuthDao.listUsers(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        return new PageResult<>(userBackDTOList, countUser);
    }

    /**
     * 统计用户地区
     */
    //todo 定时统计用户地区
    @Scheduled(cron = "0 0 * * * ?")
    public void statisticalUserArea() {
        //用户地域分布
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserAuth::getIpSource);
        List<UserAuth> userAuthList = userAuthDao.selectList(wrapper);
        Map<String, Long> userAreaMap = userAuthList
            .stream()
            .map(item -> {
                if (StringUtils.isNotBlank(item.getIpAddress())) {
                    return item.getIpSource().substring(0, 2)
                        .replaceAll(PROVINCE, "")
                        .replaceAll(CITY, "");
                } else {
                    return UNKNOWN;
                }
            }).collect(Collectors.groupingBy(item -> item, Collectors.counting()));
        //转换格式
        List<UserAreaDTO> userAreaDTOList = userAreaMap
            .entrySet()
            .stream()
            .map(userArea -> UserAreaDTO
                .builder()
                //地区名,map的键
                .name(userArea.getKey())
                //数量，map的值
                .value(userArea.getValue())
                .build())
            .collect(Collectors.toList());
        //todo 将用户地域分布存入redis，redisTemplate.opsForValue().set(key, value);
        redisService.set(USER_AREA, JSON.toJSONString(userAreaDTOList));
    }

    /**
     * 后台获取用户区域分布
     * 分两类用户/游客，地图，
     *
     * @param conditionVO 条件签证官
     * @return {@link List<UserAreaDTO>} 用户区域分布
     */
    @Override
    public List<UserAreaDTO> listUserAreas(ConditionVO conditionVO) {
        List<UserAreaDTO> userAreaDTOList = new ArrayList<>();
        switch (Objects.requireNonNull(getUserAreaType(conditionVO.getType()))) {
            case USER:
                // 查询注册用户区域分布
                //todo 从redis中获取用户地区，redisTemplate.opsForValue().get(key);
                Object userArea = redisService.get(USER_AREA);
                if (Objects.nonNull(userArea)) {
                    userAreaDTOList = JSON.parseObject(userArea.toString(), List.class);
                }
                return userAreaDTOList;
            case VISITOR:
                // 查询游客区域分布
                //todo redisTemplate.opsForHash().entries(key);从redis获取访客地区
                Map<String, Object> visitorArea = redisService.hGetAll(VISITOR_AREA);
                if (Objects.nonNull(visitorArea)) {
                    userAreaDTOList = visitorArea.entrySet().stream()
                        .map(item -> UserAreaDTO.builder()
                            .name(item.getKey())
                            .value(Long.valueOf(item.getValue().toString()))
                            .build())
                        .collect(Collectors.toList());
                }
                return userAreaDTOList;
            default:
                break;

        }
        return userAreaDTOList;
    }
}
