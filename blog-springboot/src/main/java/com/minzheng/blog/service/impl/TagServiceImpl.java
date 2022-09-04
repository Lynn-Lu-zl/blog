package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.ArticleTagDao;
import com.minzheng.blog.dao.TagDao;
import com.minzheng.blog.dto.TagBackDTO;
import com.minzheng.blog.dto.TagDTO;
import com.minzheng.blog.entity.ArticleTag;
import com.minzheng.blog.entity.Tag;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.TagService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.PageResult;
import com.minzheng.blog.vo.TagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签服务
 *
 */
@Service
public class TagServiceImpl extends ServiceImpl<TagDao, Tag> implements TagService {
    @Autowired
    private TagDao tagDao;
    @Autowired
    private ArticleTagDao articleTagDao;

    /**
     * 查询前台标签列表
     *
     * @return 标签列表
     */
    @Override
    public PageResult<TagDTO> listTags() {
        // 查询标签列表
        List<Tag> tagList = tagDao.selectList(null);
        // 转换DTO
        List<TagDTO> tagDTOList = BeanCopyUtils.copyList(tagList, TagDTO.class);
        // 查询标签数量
        Integer count = tagDao.selectCount(null);
        return new PageResult<>(tagDTOList,count);
    }

    /**
     * 查询后台标签
     *
     * @param condition 条件
     * @return {@link PageResult<TagBackDTO>} 标签列表
     */
    @Override
    public PageResult<TagBackDTO> listTagBackDTO(ConditionVO condition) {
        List<TagBackDTO> tagBackDTOList = tagDao.listTagBackDTO(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(condition.getKeywords()),Tag::getTagName,condition.getKeywords());
        Integer count = tagDao.selectCount(wrapper);
        return new PageResult<>(tagBackDTOList,count);
    }

    /**
     * 搜索文章标签
     *
     * @param condition 条件
     * @return {@link List<TagDTO>} 标签列表
     */
    @Override
    public List<TagDTO> listTagsBySearch(ConditionVO condition) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(condition.getKeywords()),Tag::getTagName,condition.getKeywords())
            .orderByDesc(Tag::getId);
        List<Tag> tagList = tagDao.selectList(wrapper);
        List<TagDTO> tagDTOList = BeanCopyUtils.copyList(tagList, TagDTO.class);
        return tagDTOList;
    }

    /**
     * 删除标签
     *在文章标签表根据tag id范围看是否有文章--》如果该标签下有文章不能删除
     * @param tagIdList 标签id集合
     */
    @Override
    public void deleteTag(List<Integer> tagIdList) {
        //如果该标签下有文章不能删除
        LambdaQueryWrapper<ArticleTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ArticleTag::getTagId,tagIdList);
        Integer count = articleTagDao.selectCount(wrapper);
        if (count > 0){
            throw new BizException("删除失败，该标签下有文章，请先删除该标签下的所有文章");
        }
        tagDao.deleteBatchIds(tagIdList);

    }

    /**
     * 保存或更新标签
     *
     * @param tagVO 标签
     */
    @Override
    public void saveOrUpdateTag(TagVO tagVO) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Tag::getId).eq(Tag::getTagName,tagVO.getTagName());
        Tag one = tagDao.selectOne(wrapper);
        if (one != null && !one.getId().equals(tagVO.getId())){
            throw new BizException("标签名不能重复添加哦！");
        }
        Tag tag = BeanCopyUtils.copyObject(tagVO, Tag.class);
        this.saveOrUpdate(tag);

    }
}
