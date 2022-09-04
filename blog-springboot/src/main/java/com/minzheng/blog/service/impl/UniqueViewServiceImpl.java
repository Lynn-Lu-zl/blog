package com.minzheng.blog.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.UniqueViewDao;
import com.minzheng.blog.dto.UniqueViewDTO;
import com.minzheng.blog.entity.UniqueView;
import com.minzheng.blog.service.RedisService;
import com.minzheng.blog.service.UniqueViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.minzheng.blog.constant.RedisPrefixConst.UNIQUE_VISITOR;
import static com.minzheng.blog.constant.RedisPrefixConst.VISITOR_AREA;
import static com.minzheng.blog.enums.ZoneEnum.SHANGHAI;


/**
 * 访问量统计服务
 */
@Service
public class UniqueViewServiceImpl extends ServiceImpl<UniqueViewDao, UniqueView> implements UniqueViewService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private UniqueViewDao uniqueViewDao;

    /**
     * 获取7天用户量统计
     *
     * @return 用户量
     */
    @Override
    public List<UniqueViewDTO> listUniqueViews() {
        //横坐标，起始日期（7天前）-截止日期（今天）

        //LocalDateTime beginOfDay(LocalDateTime time)，修改为一天的开始时间
        //DateTime offsetDay(Date date, int offset) ，向前偏移7天
        //起始日期
        DateTime startTime = DateUtil.beginOfDay(DateUtil.offsetDay(new Date(), -7));
        //LocalDateTime endOfDay(LocalDateTime time)，修改为一天的结束时间
        //截止日期
        DateTime endTime = DateUtil.endOfDay(new Date());
        //获取7天用户量统计
        List<UniqueViewDTO> uniqueViewDTOS = uniqueViewDao.listUniqueViews(startTime, endTime);
        return uniqueViewDTOS;
    }

    /**
     * 每天一次执行定时任务，同步redis的用户访问量数据到mysql
     */
    //todo 每天整点执行一次定时任务，同步redis的用户访问量数据到mysql，cron = "0 0 0 * * ?"
    @Scheduled(cron = "0 0 0 * * ?",zone = "Asia/Shanghai")
    public void saveUniqueView()
    {
        // todo redisTemplate.opsForSet().size(key)，获取Set结构的长度，获取每天用户访问量，
        Long userCount = redisService.sSize(UNIQUE_VISITOR);
        //userCount如果不为空取userCount的整数值，如果为空则取整数0作为它的值
        Integer viewsCount = Optional.of(userCount.intValue()).orElse(0);
        // 获取昨天日期插入数据
        UniqueView uniqueView = UniqueView
            .builder()
            // 偏移后的日期时间：LocalDateTime offset(LocalDateTime time, long number（偏移量，正数为向后偏移，负数为向前偏移）, TemporalUnit field（偏移单位，见ChronoUnit，不能为null，ChronoUnit.DAYS即以天为单位）)
            //今天的时间向前偏移一天=昨天
            .createTime(LocalDateTimeUtil.offset
                (LocalDateTime.now(ZoneId.of(SHANGHAI.getZone())),
                -1,
                ChronoUnit.DAYS))
            .viewsCount(viewsCount)
            .build();
        uniqueViewDao.insert(uniqueView);
    }

    /**
     * 定时任务
     * 每天1点执行一次定时任务，清除redis的用户访问量、用户分布地域数据--》和同步用户访问量到数据库错开时间
     */
    @Scheduled(cron = "0 1 0 * * ?",zone = "Asia/Shanghai")
    public void chear()
    {
        // 清空redis用户访问量记录
        // todo redisTemplate.delete(key)，删除数据
        redisService.del(UNIQUE_VISITOR);
        // 清空redis游客区域统计
        redisService.del(VISITOR_AREA);
    }
}
