package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.ChatRecordDao;
import com.minzheng.blog.entity.ChatRecord;
import com.minzheng.blog.service.ChatRecordService;
import org.springframework.stereotype.Service;

/**
 * 聊天记录服务
 */
@Service
public class ChatRecordServiceImpl extends ServiceImpl<ChatRecordDao, ChatRecord> implements ChatRecordService {
}
