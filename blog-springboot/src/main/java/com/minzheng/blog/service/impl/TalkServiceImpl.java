package com.minzheng.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.CommentDao;
import com.minzheng.blog.dao.TalkDao;
import com.minzheng.blog.dto.CommentCountDTO;
import com.minzheng.blog.dto.TalkBackDTO;
import com.minzheng.blog.dto.TalkDTO;
import com.minzheng.blog.entity.Talk;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.RedisService;
import com.minzheng.blog.service.TalkService;
import com.minzheng.blog.util.*;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.PageResult;
import com.minzheng.blog.vo.TalkVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.RedisPrefixConst.TALK_LIKE_COUNT;
import static com.minzheng.blog.constant.RedisPrefixConst.TALK_USER_LIKE;
import static com.minzheng.blog.enums.PhotoAlbumStatusEnum.PUBLIC;

/**
 * 说说服务
 *
 * @author yezhiqiu
 * @date 2022/01/23
 */
@Service
public class TalkServiceImpl extends ServiceImpl<TalkDao, Talk> implements TalkService {
    @Autowired
    private TalkDao talkDao;
    @Autowired
    private CommentDao commentDao;
    @Autowired
    private RedisService redisService;

    /**
     * 获取首页说说列表
     *
     *查询前10条说说--》公开+置顶+id优先展示
     * 遍历每条说说--》截取前200个字符长度--》封装成list
     *
     * @return {@link List<String>} 说说列表
     */
    @Override
    public List<String> listHomeTalks() {

        LambdaQueryWrapper<Talk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Talk::getStatus, PUBLIC.getStatus())
            .orderByDesc(Talk::getIsTop)
            .orderByDesc(Talk::getId)
            .last("limit 10");
        List<Talk> talkList = talkDao.selectList(wrapper);
        List<String> list = talkList
            .stream()
            .map(item -> item.getContent().length() > 200 ? HTMLUtils.deleteHMTLTag(item.getContent().substring(0, 200)) : HTMLUtils.deleteHMTLTag(item.getContent()))
            .collect(Collectors.toList());
        return list;
    }

    /**
     * 查看前台说说列表talks?current=1&size=10
     *查询说说总数量--》
     * 数量为0--》直接返回分页结果
     * 数量不为0--》分页查询说说信息+说说的评论数量+点赞量+图片
     * @return {@link PageResult<TalkDTO>} 说说列表
     */
    @Override
    public PageResult<TalkDTO> listTalks() {
        LambdaQueryWrapper<Talk> talkWrapper = new LambdaQueryWrapper<>();
        //公开状态的说说数量
        talkWrapper.eq(Talk::getStatus,PUBLIC.getStatus());
        Integer count = talkDao.selectCount(talkWrapper);
        if (count == 0)
        {
            return new PageResult<>();
        }else{
            //分页查询公开状态的所有说说信息
            List<TalkDTO> talkDTOS = talkDao.listTalks(PageUtils.getLimitCurrent(), PageUtils.getSize());
            //遍历每个说说的信息拿到所有说说的id-->list集合talkIdList
            List<Integer> talkIdList = talkDTOS
                .stream()
                .map(TalkDTO::getId)
                .collect(Collectors.toList());
            //根据所有说说的id--》查询和comment表中的topic id相同、评论类型为说说的所有父子级评论--》map（key，value）
            List<CommentCountDTO> commentCountDTOList = commentDao.listCommentCountByTopicIds(talkIdList);
            Map<Integer, Integer> commentCountMap = commentCountDTOList
                .stream()
                //将list转成map，map的key是评论id，value是评论数量
                .collect(Collectors.toMap(CommentCountDTO::getId, CommentCountDTO::getCommentCount));
            //todo redisTemplate.opsForHash().entries(key)，直接获取整个Hash结构,每个说说的点赞数量
            Map<String, Object> likeCountMap = redisService.hGetAll(TALK_LIKE_COUNT);

            talkDTOS.forEach(talkDTO -> {
                //根据map的key获取value赋值
                talkDTO.setLikeCount((Integer) likeCountMap.get(talkDTO.getId().toString()));
                //根据map的key获取value赋值
                talkDTO.setCommentCount(commentCountMap.get(talkDTO.getId()));
                //转换图片格式
                if (Objects.nonNull(talkDTO.getImages()))
                {
                    talkDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkDTO.getImages(), List.class), String.class));
                }
            });
            return new PageResult<>(talkDTOS,count);
        }

}

    /**
     * 查看后台说说，不需要显示点赞数评论数
     *
     * @param conditionVO 查询条件
     *                    按状态查询：全部，公开1，私密2
     * @return {@link PageResult<TalkBackDTO>}
     */
    @Override
    public PageResult<TalkBackDTO> listBackTalks(ConditionVO conditionVO) {
        LambdaQueryWrapper<Talk> talkWrapper = new LambdaQueryWrapper<>();
        talkWrapper.eq(Objects.nonNull(conditionVO.getStatus()),Talk::getStatus,conditionVO.getStatus());
        Integer count = talkDao.selectCount(talkWrapper);
        if (count == 0)
        {
            return new PageResult<>();
        }
        //根据查询条件获取所有的说说信息
        List<TalkBackDTO> talkBackDTOList = talkDao.listBackTalks(PageUtils.getLimitCurrent(), PageUtils.getSize(), conditionVO);
        talkBackDTOList.forEach(talkBackDTO -> {
            // 转换图片格式
            if (Objects.nonNull(talkBackDTO.getImages())) {
                talkBackDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkBackDTO.getImages(), List.class), String.class));
            }
        });
        return new PageResult<>(talkBackDTOList, count);
    }

    /**
     * 前台根据id查看具体的一条说说
     *
     * @param talkId 说说id
     * @return {@link TalkDTO} 说说信息
     */
    @Override
    public TalkDTO getTalkById(Integer talkId) {
        //查询说说信息
        TalkDTO talkDTO = talkDao.getTalkById(talkId);
        if (Objects.isNull(talkDTO))
        {
            throw new BizException("说说已经不存在啦，可能是被删除了");
        }
        //todo 从redis查询说说点赞量,获取Hash结构中的属性,redisTemplate.opsForHash().get(key, hashKey);
        Integer likeCount = (Integer) redisService.hGet(TALK_LIKE_COUNT, talkId.toString());
        talkDTO.setLikeCount(likeCount);

        //转换图片格式，有可能有多张图片
        if (Objects.nonNull(talkDTO.getImages()))
        {
            //JSON.parseObject(talkDTO.getImages(), List.class)将JSON字符串解析成java集合对象--》字符串对象？
            talkDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkDTO.getImages(), List.class), String.class));
        }

        return talkDTO;
    }

    /**
     * 点赞说说
     * redis
     * 判断是否点过赞--》
     * 没点过赞：点赞--》redis根据说说id为key添加点赞信息+该说说的点赞数量增加1
     * 已经点过赞：取消点赞--》redis根据说说id的key移除点赞信息+该说说的点赞数量减1
     * @param talkId 说说id
     */
    //todo 点赞说说
    @Override
    public void saveTalkLike(Integer talkId) {
        //存在redis的key：talk_user_like:1（用户id），记录某个用户点赞了哪些说说
        String talkLikeKey = TALK_USER_LIKE + UserUtils.getLoginUser().getUserInfoId();
        //todo redisTemplate.opsForSet().isMember(key, value),是否为Set中的属性
        if ( ! redisService.sIsMember(talkLikeKey,talkId))
        {
            //没点过赞则可以点赞
            //todo 根据说说id为该用户添加点赞信息，redisTemplate.opsForSet().add(key, values)，向Set结构中添加属性
            redisService.sAdd(talkLikeKey,talkId);
            //todo 说说点赞数加一，redisTemplate.opsForHash().increment(key点赞数, hashKey说说的id, 数量单位为1)，Hash结构中属性递增
            redisService.hIncr(TALK_LIKE_COUNT,talkId.toString(),1L);
        }else{
            //已经点过赞-->取消点赞
            //todo 根据说说id为该用户添加点赞信息，redisTemplate.opsForSet().remove(key, values)，删除Set结构中的属性
            redisService.sRemove(talkLikeKey,talkId);
            //todo 说说点赞数减一redisTemplate.opsForHash().increment(key, hashKey, -delta)，Hash结构中属性递减
            redisService.hDecr(TALK_LIKE_COUNT,talkId.toString(),1L);
        }

    }

    /**
     * 保存或修改说说
     *
     * @param talkVO 说说信息
     */
    @Transactional
    @Override
    public void saveOrUpdateTalk(TalkVO talkVO) {
        Talk talk = BeanCopyUtils.copyObject(talkVO, Talk.class);
        talk.setUserId(UserUtils.getLoginUser().getUserInfoId());
        this.saveOrUpdate(talk);
    }

    /**
     * 批量删除说说
     *
     * @param talkIdList 说说id列表
     */
    @Override
    public void deleteTalks(List<Integer> talkIdList) {
        talkDao.deleteBatchIds(talkIdList);
    }



    /**
     * 后台根据id查看具体的一条说说
     *
     * @param talkId 说说id
     * @return {@link TalkBackDTO} 说说信息
     */
    @Override
    public TalkBackDTO getBackTalkById(Integer talkId) {
        TalkBackDTO talkBackDTO = talkDao.getBackTalkById(talkId);
        //转换图片格式，
        if (Objects.nonNull(talkBackDTO.getImages()))
        {
            talkBackDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkBackDTO.getImages(), List.class), String.class));
        }
        return talkBackDTO;
    }
}
