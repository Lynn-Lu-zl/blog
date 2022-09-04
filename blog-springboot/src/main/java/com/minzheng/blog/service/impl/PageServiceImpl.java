package com.minzheng.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.PageDao;
import com.minzheng.blog.entity.Page;
import com.minzheng.blog.service.PageService;
import com.minzheng.blog.service.RedisService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.vo.PageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.minzheng.blog.constant.RedisPrefixConst.PAGE_COVER;

/**
 * 页面服务
 *
 */
@Service
public class PageServiceImpl extends ServiceImpl<PageDao, Page> implements PageService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private PageDao pageDao;

    /**
     * 保存或更新页面
     * 赋值属性--》删除redis缓存
     *
     * @param pageVO 页面信息
     */
    @Transactional
    @Override
    public void saveOrUpdatePage(PageVO pageVO) {
        Page page = BeanCopyUtils.copyObject(pageVO, Page.class);
        this.saveOrUpdate(page);
        // 删除redis缓存,等它进入查询方法时就从数据库获取再存入redis
        redisService.del(PAGE_COVER);
    }

    /**
     * 删除页面
     *
     * @param pageId 页面id
     */
    @Transactional
    @Override
    public void deletePage(Integer pageId) {
        pageDao.deleteById(pageId);
        // 删除redis缓存,等它进入查询方法时就从数据库获取再存入redis
        redisService.del(PAGE_COVER);
    }

    /**
     * 获取页面列表
     * 有--》从redis获取缓存
     * 没有--》从数据库查询--》缓存到redis中
     *
     *
     * @return {@link List<PageVO>} 页面列表
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<PageVO> listPages() {
        List<PageVO> pageVOList;
        Object pageList = redisService.get(PAGE_COVER);
        if (Objects.nonNull(pageList)) {
            //将JSON字符串解析java对象
            pageVOList = JSON.parseObject(pageList.toString(),List.class);
        }
        else{
            pageVOList = BeanCopyUtils.copyList(pageDao.selectList(null),PageVO.class);
            //将java对象转换成JSON字符串，存入redis
            redisService.set(PAGE_COVER,JSON.toJSONString(pageVOList));
        }

        return pageVOList;
    }
}
