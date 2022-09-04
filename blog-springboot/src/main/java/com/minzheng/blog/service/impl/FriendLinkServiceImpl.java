package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.minzheng.blog.dao.FriendLinkDao;
import com.minzheng.blog.dto.FriendLinkBackDTO;
import com.minzheng.blog.dto.FriendLinkDTO;
import com.minzheng.blog.entity.FriendLink;

import com.minzheng.blog.service.FriendLinkService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.FriendLinkVO;
import com.minzheng.blog.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 友情链接服务
 *
 */
@Service
public class FriendLinkServiceImpl extends ServiceImpl<FriendLinkDao, FriendLink> implements FriendLinkService {
    @Autowired
    private FriendLinkDao friendLinkDao;

    /**
     * 查看友链列表
     *
     * @return 友链列表
     */
    @Override
    public List<FriendLinkDTO> listFriendLinks() {
        List<FriendLink> friendLinkList = friendLinkDao.selectList(null);
        return BeanCopyUtils.copyList(friendLinkList, FriendLinkDTO.class);
    }

    /**
     * 查看后台友链列表
     *
     * @param condition 条件
     * @return 友链列表
     */
    @Override
    public PageResult<FriendLinkBackDTO> listFriendLinkDTO(ConditionVO condition) {
        //分页查询
        Page<FriendLink> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
        LambdaQueryWrapper<FriendLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(condition.getKeywords()),FriendLink::getLinkName,condition.getKeywords());
        Page<FriendLink> friendLinkPage = friendLinkDao.selectPage(page, wrapper);
        List<FriendLinkBackDTO> friendLinkBackDTOS = BeanCopyUtils.copyList(friendLinkPage.getRecords(), FriendLinkBackDTO.class);
        return new PageResult<>(friendLinkBackDTOS, (int) friendLinkPage.getTotal());
    }

    /**
     * 保存或更新友链
     *
     * @param friendLinkVO 友链
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateFriendLink(FriendLinkVO friendLinkVO) {
        FriendLink friendLink = BeanCopyUtils.copyObject(friendLinkVO, FriendLink.class);
        this.saveOrUpdate(friendLink);
    }
}
