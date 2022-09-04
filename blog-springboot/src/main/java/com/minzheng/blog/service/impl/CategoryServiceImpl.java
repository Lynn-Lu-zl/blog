package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.ArticleDao;
import com.minzheng.blog.dao.CategoryDao;
import com.minzheng.blog.dto.CategoryBackDTO;
import com.minzheng.blog.dto.CategoryDTO;
import com.minzheng.blog.dto.CategoryOptionDTO;
import com.minzheng.blog.entity.Article;
import com.minzheng.blog.entity.Category;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.CategoryService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.CategoryVO;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 分类服务
 *
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, Category> implements CategoryService {
    @Autowired
    private CategoryDao categoryDao;
    @Autowired
    private ArticleDao articleDao;

    /**
     * 前台查询分类列表
     *
     * @return 分类列表
     */
    @Override
    public PageResult<CategoryDTO> listCategories() {
        // 查询所有分类和对应文章数量
        List<CategoryDTO> categoryDTOList = categoryDao.listCategoryDTO();
        //前台显示所有的分类数量
        Integer count = categoryDao.selectCount(null);
        return new PageResult<>(categoryDTOList, count);
    }

    /**
     * 查询后台所有分类
     *
     * @param conditionVO 条件
     * @return {@link PageResult<CategoryBackDTO>} 后台分类
     */
    @Override
    public PageResult<CategoryBackDTO> listBackCategories(ConditionVO conditionVO) {
        //查询后台分类列表
        List<CategoryBackDTO> categoryBackDTOList = categoryDao.listCategoryBackDTO(PageUtils.getLimitCurrent(), PageUtils.getSize(), conditionVO);
        //如果
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(conditionVO.getKeywords()),Category::getCategoryName,conditionVO.getKeywords());
        Integer count = categoryDao.selectCount(wrapper);
        return new PageResult<>(categoryBackDTOList,count);
    }

    /**
     * 后台搜索文章分类
     *
     * @param condition 条件
     * @return {@link List<CategoryOptionDTO>} 分类列表
     */
    @Override
    public List<CategoryOptionDTO> listCategoriesBySearch(ConditionVO condition) {
        // 搜索分类
        List<Category> categoryList = categoryDao.selectList(new LambdaQueryWrapper<Category>()
            .like(StringUtils.isNotBlank(condition.getKeywords()), Category::getCategoryName, condition.getKeywords())
            .orderByDesc(Category::getId));
        return BeanCopyUtils.copyList(categoryList, CategoryOptionDTO.class);
    }

    /**
     * 删除分类
     *
     * @param categoryIdList 分类id集合
     */
    @Override
    public void deleteCategory(List<Integer> categoryIdList) {
        //如果该分类id下有文章不能删除
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Article::getCategoryId,categoryIdList);
        Integer count = articleDao.selectCount(wrapper);
        if (count > 0){
            throw new BizException("删除失败，该分类下有文章，请先删除该分类下的文章");
        }else{
            categoryDao.deleteBatchIds(categoryIdList);
        }
    }

    /**
     * 添加或修改分类
     *
     * @param categoryVO 分类
     */
    @Override
    public void saveOrUpdateCategory(CategoryVO categoryVO) {
        //分类名不能重复
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper
            .select(Category::getId)
            .eq(Category::getCategoryName, categoryVO.getCategoryName());
        Category one = categoryDao.selectOne(wrapper);
        if (one != null && !one.getId().equals(categoryVO.getId())) {
            throw new BizException("分类名不能重复添加哦！");
        } else {
            Category category = BeanCopyUtils.copyObject(categoryVO, Category.class);
            this.saveOrUpdate(category);
        }
    }
}
