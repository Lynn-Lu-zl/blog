package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.MessageDao;
import com.minzheng.blog.dto.MessageBackDTO;
import com.minzheng.blog.dto.MessageDTO;
import com.minzheng.blog.entity.Message;
import com.minzheng.blog.service.BlogInfoService;
import com.minzheng.blog.service.MessageService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.HTMLUtils;
import com.minzheng.blog.util.IpUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.MessageVO;
import com.minzheng.blog.vo.PageResult;
import com.minzheng.blog.vo.ReviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.FALSE;
import static com.minzheng.blog.constant.CommonConst.TRUE;

/**
 * 留言服务
 *
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageDao, Message> implements MessageService {
    @Autowired
    private MessageDao messageDao;
    @Resource
    private HttpServletRequest request;
    @Autowired
    private BlogInfoService blogInfoService;

    /**
     * 添加留言弹幕
     *
     * @param messageVO 留言对象
     */
    @Override
    public void saveMessage(MessageVO messageVO) {
        // 判断是否需要审核
        Integer isMessageReview = blogInfoService.getWebsiteConfig().getIsMessageReview();
        String ipAddress = IpUtils.getIpAddress(request);
        String ipSource = IpUtils.getIpSource(ipAddress);
        Message message = BeanCopyUtils.copyObject(messageVO, Message.class);
        //todo 需要进行剔除HTML的文本---》过滤敏感词
        message.setMessageContent(HTMLUtils.filter(message.getMessageContent()));
        message.setIpAddress(ipAddress);
        message.setIsReview(isMessageReview == TRUE ? FALSE : TRUE);
        message.setIpSource(ipSource);
        //插入数据
        messageDao.insert(message);
    }

    /**
     * 查看留言弹幕
     *
     * @return 留言列表
     */
    @Override
    public List<MessageDTO> listMessages() {
        // 查询留言列表
        List<Message> messageList = messageDao.selectList(new LambdaQueryWrapper<Message>()
            .select(Message::getId, Message::getNickname, Message::getAvatar, Message::getMessageContent, Message::getTime)
            //审核过了的留言
            .eq(Message::getIsReview, TRUE));
        List<MessageDTO> messageDTOList = BeanCopyUtils.copyList(messageList, MessageDTO.class);
        return messageDTOList;
    }

    /**
     * 审核留言
     *
     * @param reviewVO 审查签证官
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateMessagesReview(ReviewVO reviewVO) {
        List<Message> messageList = reviewVO.getIdList().stream().map(item -> Message.builder()
            .id(item)
            //修改审核状态
            .isReview(reviewVO.getIsReview())
            .build())
            .collect(Collectors.toList());
        this.updateBatchById(messageList);

    }

    /**
     * 查看后台留言
     *
     * @param condition 条件
     * @return 留言列表
     */
    @Override
    public PageResult<MessageBackDTO> listMessageBackDTO(ConditionVO condition) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        //如果关键字不为空按关键字查询
        wrapper.like(StringUtils.isNotBlank(condition.getKeywords()),Message::getMessageContent,condition.getKeywords())
            //审核的，不审核的要分开查询
            .eq(Objects.nonNull(condition.getIsReview()), Message::getIsReview, condition.getIsReview())
            .orderByDesc(Message::getId);
        // 分页查询留言列表
        Page<Message> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
        Page<Message> messagePage = messageDao.selectPage(page, wrapper);
        // 转换DTO
        List<MessageBackDTO> messageBackDTOList = BeanCopyUtils.copyList(messagePage.getRecords(), MessageBackDTO.class);
        //获取分页查询记录详情，总数
        return new PageResult<>(messageBackDTOList,(int)messagePage.getTotal());
    }
}
