package com.minzheng.blog.consumer;

import com.alibaba.fastjson.JSON;
import com.minzheng.blog.dao.ElasticsearchDao;
import com.minzheng.blog.dto.ArticleSearchDTO;
import com.minzheng.blog.dto.MaxwellDataDTO;
import com.minzheng.blog.entity.Article;
import com.minzheng.blog.util.BeanCopyUtils;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.minzheng.blog.constant.MQPrefixConst.MAXWELL_QUEUE;

/**
 * maxwell监听数据
 * MQ消费端
 * 解析maxwell数据
 * 使用maxwell实时同步mysql数据到消息队列(rabbitMQ)--》es从消息队列获取mysql数据的变化--》也对es的数据进行相应的变化
 *
 */
@Component
@RabbitListener(queues = MAXWELL_QUEUE)
public class MaxWellConsumer {
    @Autowired
    private ElasticsearchDao elasticsearchDao;

    //todo rabbit mq maxwell监听数据
    @RabbitHandler
    public void process(byte[] data) {
        // 获取监听信息
        MaxwellDataDTO maxwellDataDTO = JSON.parseObject(new String(data), MaxwellDataDTO.class);
        // 获取文章数据
        Article article = JSON.parseObject(JSON.toJSONString(maxwellDataDTO.getData()), Article.class);
        // 判断操作类型
        switch (maxwellDataDTO.getType()) {
            case "insert":
            case "update":
                // 更新es文章
                elasticsearchDao.save(BeanCopyUtils.copyObject(article, ArticleSearchDTO.class));
                break;
            case "delete":
                // 删除文章
                elasticsearchDao.deleteById(article.getId());
                break;
            default:
                break;
        }
    }

}