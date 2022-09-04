package com.minzheng.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.ArticleDao;
import com.minzheng.blog.dao.CommentDao;
import com.minzheng.blog.dao.TalkDao;
import com.minzheng.blog.dao.UserInfoDao;
import com.minzheng.blog.dto.*;
import com.minzheng.blog.entity.Comment;
import com.minzheng.blog.service.BlogInfoService;
import com.minzheng.blog.service.CommentService;
import com.minzheng.blog.service.RedisService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.HTMLUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.util.UserUtils;
import com.minzheng.blog.vo.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.*;
import static com.minzheng.blog.constant.MQPrefixConst.EMAIL_EXCHANGE;
import static com.minzheng.blog.constant.RedisPrefixConst.COMMENT_LIKE_COUNT;
import static com.minzheng.blog.constant.RedisPrefixConst.COMMENT_USER_LIKE;
import static com.minzheng.blog.enums.CommentTypeEnum.getCommentEnum;
import static com.minzheng.blog.enums.CommentTypeEnum.getCommentPath;

/**
 * 评论服务
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentDao, Comment> implements CommentService {
    @Autowired
    private CommentDao commentDao;
    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private TalkDao talkDao;
    @Autowired
    private RedisService redisService;
    @Autowired
    private UserInfoDao userInfoDao;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private BlogInfoService blogInfoService;

    /**
     * 网站网址
     */
    @Value("${website.url}")
    private String websiteUrl;

    /**
     * 前台查看评论
     *comments?current=1&type=3&topicId=66
     *评论类型：1文章、2友链，3、说说
     * @param commentVO 评论信息
     * @return 评论列表
     */
    @Override
    public PageResult<CommentDTO> listComments(CommentVO commentVO) {

        //根据说说id查询该说说有多少评论量
        Integer commentCount = commentDao.selectCount(new LambdaQueryWrapper<Comment>()
            .eq(Comment::getTopicId, commentVO.getTopicId()).isNull(Comment::getParentId).eq(Comment::getIsReview, TRUE));
        if (commentCount == 0) {
            return new PageResult<>();
        }else{
            // 分页查询评论数据
            List<CommentDTO> commentDTOList = commentDao.listComments(PageUtils.getLimitCurrent(), PageUtils.getSize(), commentVO);
            if (CollectionUtils.isEmpty(commentDTOList)) {
                return new PageResult<>();
            }
            //todo 查询redis的评论点赞数据 Hash结构 redisTemplate.opsForHash().entries(key)
            Map<String, Object> likeCountMap = redisService.hGetAll(COMMENT_LIKE_COUNT);

            //遍历

            //遍历commentDTOList提取评论id集合---》根据评论id集合查询回复数据replyDTOList--》从redis获取点赞数，给该说说下的每条回复点赞量赋值
            List<Integer> commentIdList = commentDTOList
                .stream()
                .map(CommentDTO::getId)
                .collect(Collectors.toList());
            //根据评论的id集合查询回复数据replyDTOList
            List<ReplyDTO> replyDTOList = commentDao.listReplies(commentIdList);
            //给该评论下的每条回复点赞量赋值
            for (ReplyDTO replyDTO : replyDTOList) {
                replyDTO.setLikeCount((Integer) likeCountMap.get(replyDTO.getId().toString()));
            }

            // 根据评论id分组回复数据
            Map<Integer, List<ReplyDTO>> replyMap = replyDTOList
                .stream()
                .collect(Collectors.groupingBy(ReplyDTO::getParentId));
            // 根据评论id查询回复量
            Map<Integer, Integer> replyCountMap = commentDao.listReplyCountByCommentId(commentIdList)
                .stream()
                .collect(Collectors.toMap(ReplyCountDTO::getCommentId, ReplyCountDTO::getReplyCount));
            //根据map的key获取value给评论回复 commentDTOList赋值
            commentDTOList.forEach(commentDTO -> {
                commentDTO.setLikeCount((Integer) likeCountMap.get(commentDTO.getId().toString()));
                commentDTO.setReplyCount(replyCountMap.get(commentDTO.getId()));
                commentDTO.setReplyDTOList(replyMap.get(commentDTO.getId()));
            });
            return new PageResult<>(commentDTOList,commentCount);
        }
    }

    /**
     * 查询后台评论
     *
     * @param condition 条件
     * @return 评论列表
     */
    @Override
    public PageResult<CommentBackDTO> listCommentBackDTO(ConditionVO condition) {
        //统计后台评论量，分页要用，共x条
        Integer countCommentDTO = commentDao.countCommentDTO(condition);
        //后台评论信息
        List<CommentBackDTO> commentBackDTOList = commentDao.listCommentBackDTO(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        return new PageResult<>(commentBackDTOList,countCommentDTO);
    }

    /**
     * 查看评论下的回复，因为查看评论时看别人回复的只能显示前三条回复，如果要查看所有回复需要专门的请求
     *
     * @param commentId 评论id
     * @return 回复列表
     */
    @Override
    public List<ReplyDTO> listRepliesByCommentId(Integer commentId) {

        // 转换页码查询评论下的回复
        List<ReplyDTO> replyDTOList = commentDao.listRepliesByCommentId(PageUtils.getLimitCurrent(), PageUtils.getSize(), commentId);
        // 查询redis的评论点赞数据
        //todo 查询redis的评论点赞数据 Hash结构 redisTemplate.opsForHash().entries(key)
        Map<String, Object> likeCountMap = redisService.hGetAll(COMMENT_LIKE_COUNT);
        //给该评论下的每条回复点赞量赋值
        for (ReplyDTO replyDTO : replyDTOList) {
            replyDTO.setLikeCount((Integer) likeCountMap.get(replyDTO.getId().toString()));
        }
        return replyDTOList;
    }

    /**
     * 点赞评论
     *
     * @param commentId 评论id
     */
    @Override
    public void saveCommentLike(Integer commentId) {
        //存在redis的key：talk_user_like:1（用户id），记录某个用户点赞了哪些说说
        String commentLikeKey = COMMENT_USER_LIKE + UserUtils.getLoginUser().getUserInfoId();
        //todo redisTemplate.opsForSet().isMember(key, value),是否为Set中的属性
        if ( ! redisService.sIsMember(commentLikeKey,commentId))
        {
            //没点过赞则可以点赞
            //todo 根据评论id为该用户添加点赞信息，redisTemplate.opsForSet().add(key, values)，向Set结构中添加属性
            redisService.sAdd(commentLikeKey,commentId);
            //todo 评论点赞数加一，redisTemplate.opsForHash().increment(key点赞数, hashKey评论的id, 数量单位为1)，Hash结构中属性递增
            redisService.hIncr(COMMENT_LIKE_COUNT,commentId.toString(),1L);
        }else{
            //已经点过赞-->取消点赞
            //todo 根据评论id为该用户添加点赞信息，redisTemplate.opsForSet().remove(key, values)，删除Set结构中的属性
            redisService.sRemove(commentLikeKey,commentId);
            //todo 评论点赞数减一redisTemplate.opsForHash().increment(key, hashKey, -delta)，Hash结构中属性递减
            redisService.hDecr(COMMENT_LIKE_COUNT,commentId.toString(),1L);
        }

    }

    /**
     * 通知评论用户
     *
     * @param comment 评论信息
     */
    public void notice(Comment comment) {
        // 查询回复用户邮箱号
        Integer userId = BLOGGER_ID;
        String id = Objects.nonNull(comment.getTopicId()) ? comment.getTopicId().toString() : "";
        if (Objects.nonNull(comment.getReplyUserId())) {
            userId = comment.getReplyUserId();
        } else {
            switch (Objects.requireNonNull(getCommentEnum(comment.getType()))) {
                case ARTICLE:
                    userId = articleDao.selectById(comment.getTopicId()).getUserId();
                    break;
                case TALK:
                    userId = talkDao.selectById(comment.getTopicId()).getUserId();
                    break;
                default:
                    break;
            }
        }
        String email = userInfoDao.selectById(userId).getEmail();
        if (StringUtils.isNotBlank(email)) {
            // 发送消息
            EmailDTO emailDTO = new EmailDTO();
            if (comment.getIsReview().equals(TRUE)) {
                // 评论提醒
                emailDTO.setEmail(email);
                emailDTO.setSubject("评论提醒");
                // 获取评论路径
                String url = websiteUrl + getCommentPath(comment.getType()) + id;
                emailDTO.setContent("您收到了一条新的回复，请前往" + url + "\n页面查看");
            } else {
                // 管理员审核提醒
                String adminEmail = userInfoDao.selectById(BLOGGER_ID).getEmail();
                emailDTO.setEmail(adminEmail);
                emailDTO.setSubject("审核提醒");
                emailDTO.setContent("您收到了一条新的回复，请前往后台管理页面审核");
            }
            //todo rabbit mq 邮件提醒
            rabbitTemplate.convertAndSend(EMAIL_EXCHANGE, "*", new Message(JSON.toJSONBytes(emailDTO), new MessageProperties()));
        }
    }
    /**
     * 添加评论
     *
     * @param commentVO 评论对象
     */
    @Override
    public void saveComment(CommentVO commentVO) {
        // 看网站配置判断是否需要审核
        WebsiteConfigVO websiteConfig = blogInfoService.getWebsiteConfig();
        Integer isReview = websiteConfig.getIsCommentReview();
        // todo 过滤标签，需要进行剔除HTML的文本--》过滤敏感词
        commentVO.setCommentContent(HTMLUtils.filter(commentVO.getCommentContent()));
        Comment comment = BeanCopyUtils.copyObject(commentVO, Comment.class);
        comment.setUserId(UserUtils.getLoginUser().getUserInfoId());
        //如果配置是1则需要审核，进入审核状态
        comment.setIsReview(isReview == TRUE ? FALSE : TRUE);
        //插入数据
        commentDao.insert(comment);
        // 判断是否开启邮箱通知,通知用户
        if (websiteConfig.getIsEmailNotice().equals(TRUE)) {
            CompletableFuture.runAsync(() -> notice(comment));
        }
    }

    /**
     * 审核评论
     *
     * @param reviewVO 审核信息
     */
    @Override
    public void updateCommentsReview(ReviewVO reviewVO) {
        // 修改评论审核状态
        List<Comment> commentList = reviewVO.getIdList().stream().map(item ->
            Comment.builder()
                .id(item)
                .isReview(reviewVO.getIsReview())
                .build())
            .collect(Collectors.toList());
        this.updateBatchById(commentList);
    }
}
