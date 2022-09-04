package com.minzheng.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.UserInfoDao;
import com.minzheng.blog.dto.UserDetailDTO;
import com.minzheng.blog.dto.UserOnlineDTO;
import com.minzheng.blog.entity.UserInfo;
import com.minzheng.blog.entity.UserRole;
import com.minzheng.blog.enums.FilePathEnum;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.RedisService;
import com.minzheng.blog.service.UserInfoService;
import com.minzheng.blog.service.UserRoleService;
import com.minzheng.blog.strategy.context.UploadStrategyContext;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.UserUtils;
import com.minzheng.blog.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.RedisPrefixConst.USER_CODE_KEY;
import static com.minzheng.blog.util.PageUtils.getLimitCurrent;
import static com.minzheng.blog.util.PageUtils.getSize;


/**
 * 用户信息服务
 *
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoDao, UserInfo> implements UserInfoService {
    @Autowired
    private UserInfoDao userInfoDao;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private SessionRegistry sessionRegistry;
    @Autowired
    private RedisService redisService;
    @Autowired
    private UploadStrategyContext uploadStrategyContext;

    /**
     * 修改用户资料
     *
     * @param userInfoVO 用户资料
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserInfo(UserInfoVO userInfoVO) {
        UserInfo userInfo = BeanCopyUtils.copyObject(userInfoVO, UserInfo.class);
        userInfo.setId(UserUtils.getLoginUser().getId());
        userInfoDao.updateById(userInfo);
    }

    /**
     * 修改用户头像
     *
     * @param file 头像图片
     * @return 头像地址
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String updateUserAvatar(MultipartFile file) {
        //todo 策略模式，FilePathEnum.AVATAR.getPath()：头像路径
        String avatar = uploadStrategyContext.executeUploadStrategy(file, FilePathEnum.AVATAR.getPath());
        UserInfo userInfo = UserInfo
            .builder()
            .avatar(avatar)
            .id(UserUtils.getLoginUser().getId())
            .build();
        userInfoDao.updateById(userInfo);
        return avatar;
    }

    /**
     * 绑定用户邮箱
     *
     * @param emailVO 邮箱
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveUserEmail(EmailVO emailVO) {
        //todo redisTemplate.opsForValue().get(key);从redis中获取邮箱验证码
        String code = redisService.get(USER_CODE_KEY + emailVO.getEmail()).toString();
        if (! emailVO.getCode().equals(code)){
            throw new BizException("邮箱验证码错误！");
        }
        UserInfo userInfo = UserInfo
            .builder()
            .id(UserUtils.getLoginUser().getId())
            .email(emailVO.getEmail())
            .build();
        userInfoDao.updateById(userInfo);

    }

    /**
     * 更新用户角色
     *
     * @param userRoleVO 更新用户角色
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserRole(UserRoleVO userRoleVO) {
        //修改用户角色和昵称
        UserInfo userInfo = UserInfo
            .builder()
            .id(userRoleVO.getUserInfoId())
            .nickname(userRoleVO.getNickname())
            .build();
        userInfoDao.updateById(userInfo);

        //先删除用户角色重新添加
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId,userRoleVO.getUserInfoId());
        userRoleService.remove(userRoleWrapper);
        //重新添加
        List<UserRole> userRoleList = userRoleVO.getRoleIdList()
            .stream()
            .map(roleId -> UserRole
                .builder()
                .roleId(roleId)
                .userId(userRoleVO.getUserInfoId())
                .build())
            .collect(Collectors.toList());
        userRoleService.saveBatch(userRoleList);


    }

    /**
     * 修改用户禁用状态
     *
     * @param userDisableVO 用户禁用信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserDisable(UserDisableVO userDisableVO) {
        UserInfo userInfo = BeanCopyUtils.copyObject(userDisableVO, UserInfo.class);
        userInfoDao.updateById(userInfo);
    }

    /**
     * 查看在线用户列表
     *
     * @param conditionVO 条件
     * @return 在线用户列表
     */
    @Override
    public PageResult<UserOnlineDTO> listOnlineUsers(ConditionVO conditionVO) {
        // 获取security在线session
        List<UserOnlineDTO> userOnlineDTOList = sessionRegistry.getAllPrincipals().stream()
            .filter(item -> sessionRegistry.getAllSessions(item, false).size() > 0)
            .map(item -> JSON.parseObject(JSON.toJSONString(item), UserOnlineDTO.class))
            .filter(item -> StringUtils.isBlank(conditionVO.getKeywords()) || item.getNickname().contains(conditionVO.getKeywords()))
            .sorted(Comparator.comparing(UserOnlineDTO::getLastLoginTime).reversed())
            .collect(Collectors.toList());
        // 执行分页
        //静态方法
        int fromIndex = getLimitCurrent().intValue();
        //静态方法
        int size = getSize().intValue();
        int toIndex = userOnlineDTOList.size() - fromIndex > size ? fromIndex + size : userOnlineDTOList.size();
        List<UserOnlineDTO> userOnlineList = userOnlineDTOList.subList(fromIndex, toIndex);
        return new PageResult<>(userOnlineList, userOnlineDTOList.size());
    }

    /**
     * 下线用户
     *
     * @param userInfoId 用户信息id
     */
    @Override
    public void removeOnlineUser(Integer userInfoId) {
        // 获取用户session
        List<Object> userInfoList = sessionRegistry.getAllPrincipals().stream().filter(item -> {
            UserDetailDTO userDetailDTO = (UserDetailDTO) item;
            return userDetailDTO.getUserInfoId().equals(userInfoId);
        }).collect(Collectors.toList());
        List<SessionInformation> allSessions = new ArrayList<>();
        userInfoList.forEach(item -> allSessions.addAll(sessionRegistry.getAllSessions(item, false)));
        // 注销session
        allSessions.forEach(SessionInformation::expireNow);
    }
}
