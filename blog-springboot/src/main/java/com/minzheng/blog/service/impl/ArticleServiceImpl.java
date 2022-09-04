package com.minzheng.blog.service.impl;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.*;
import com.minzheng.blog.dto.*;
import com.minzheng.blog.entity.Article;
import com.minzheng.blog.entity.ArticleTag;
import com.minzheng.blog.entity.Category;
import com.minzheng.blog.entity.Tag;
import com.minzheng.blog.enums.FileExtEnum;
import com.minzheng.blog.enums.FilePathEnum;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.*;
import com.minzheng.blog.strategy.context.SearchStrategyContext;
import com.minzheng.blog.strategy.context.UploadStrategyContext;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.util.UserUtils;
import com.minzheng.blog.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.FALSE;
import static com.minzheng.blog.constant.RedisPrefixConst.*;
import static com.minzheng.blog.enums.ArticleStatusEnum.DRAFT;
import static com.minzheng.blog.enums.PhotoAlbumStatusEnum.PUBLIC;


/**
 * 文章服务
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleDao, Article> implements ArticleService  {
    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private ArticleService articleService;

    @Autowired
    private CategoryDao categoryDao;
    @Autowired
    private TagDao tagDao;
    @Autowired
    private TagService tagService;
    @Autowired
    private ArticleTagDao articleTagDao;
    @Autowired
    private SearchStrategyContext searchStrategyContext;
    @Autowired
    private HttpSession session;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ArticleTagService articleTagService;
    @Autowired
    private BlogInfoService blogInfoService;
    @Autowired
    private UploadStrategyContext uploadStrategyContext;

    @Autowired
    private ElasticsearchDao elasticsearchDao;

    /**
     * 先删除全部数据，再重新导入数据
     */
    public void importDataIntoES() {
        elasticsearchDao.deleteAll();
        List<Article> articles = articleService.list();
        for (Article article : articles) {
            elasticsearchDao.save(BeanCopyUtils.copyObject(article, ArticleSearchDTO.class));
        }
    }

    /**
     * 查询文章归档
     *从数据库中查出所有的文章--复制属性到ArchiveDTO
     * @return 文章归档
     */
    @Override
    public PageResult<ArchiveDTO> listArchives() {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Article::getId, Article::getArticleTitle, Article::getCreateTime)
            .orderByDesc(Article::getCreateTime)
            .eq(Article::getStatus,PUBLIC.getStatus())
            .eq(Article::getIsDelete, FALSE);
        Page<Article> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
        Page<Article> articlePage = articleDao.selectPage(page, wrapper);
        List<ArchiveDTO> archiveDTOList = BeanCopyUtils.copyList(articlePage.getRecords(), ArchiveDTO.class);
        //importDataIntoES();
        //强转成PageResult类型
        PageResult<ArchiveDTO> result = new PageResult<>(archiveDTOList, (int) articlePage.getTotal());
        return result;
    }

    /**
     * 查询后台文章
     *
     * @param condition 条件
     * @return 文章列表
     */
    @Override
    public PageResult<ArticleBackDTO> listArticleBacks(ConditionVO condition) {
        //查询文章总数量
        Integer countArticleBacks = articleDao.countArticleBacks(condition);
        //查询后台文章，但是文章点赞量和浏览量likeCount、viewsCount要从redis中获取
        List<ArticleBackDTO> articleBackDTOList = articleDao.listArticleBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);

        //从redis中获取所有的文章点赞量和浏览量likeCount、viewsCount集合
        //todo redisTemplate.opsForHash().entries(key);
        Map<String, Object> likeCountMap = redisService.hGetAll(ARTICLE_LIKE_COUNT);
        //todo redisTemplate.opsForZSet().rangeWithScores(key, 0, -1)然后用stream流遍历zset--》map
        Map<Object, Double> viewsCountMap = redisService.zAllScore(ARTICLE_VIEWS_COUNT);
        //遍历赋值给对应的文章
        articleBackDTOList.forEach(articleBackDTO -> {
            //根据viewsCountMap的key获取浏览量的值
                Double viewsCount = viewsCountMap.get(articleBackDTO.getId());
            if (Objects.nonNull(viewsCount)) {
                //将包装类变成基本类型int赋值
                articleBackDTO.setViewsCount(viewsCount.intValue());
            }
            //todo 赋值点赞量
            //Map<String, Object>' may not contain objects of type 'Integer' less，likeCountMap可能不包含整数类型的对象--》先转成string类型获取点赞量再强转为int类型展示到后台页面
            Integer likeCount = (Integer) likeCountMap.get(articleBackDTO.getId().toString());
            articleBackDTO.setLikeCount(likeCount);
        });
        importDataIntoES();
        return new PageResult<>(articleBackDTOList, countArticleBacks);
    }

    /**
     * 查询首页文章
     *，先用子查询查出文章表中所有的文章--》左连接将tag_id,category_id变成具体的name
     * @return 文章列表
     */
    @Override
    public List<ArticleHomeDTO> listArticles() {
        List<ArticleHomeDTO> listArticles = articleDao.listArticles(PageUtils.getLimitCurrent(), PageUtils.getSize());
        return listArticles;
    }

    /**
     * 根据条件查询文章列表
     *
     * @param condition 条件
     * @return 文章列表
     */
    @Override
    public ArticlePreviewListDTO listArticlesByCondition(ConditionVO condition) {
        //根据条件查询文章
        List<ArticlePreviewDTO> articlePreviewDTOList = articleDao.listArticlesByCondition(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        // 搜索条件对应名(标签或分类名)
        String name=null;
        if (condition.getCategoryId() != null){
            //根据分类id找到该分类名赋值给name
            LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
            categoryWrapper.select(Category::getCategoryName).eq(Category::getId,condition.getCategoryId());
            Category category = categoryDao.selectOne(categoryWrapper);
            name = category.getCategoryName();
        }else if (condition.getTagId() != null){
            // 根据标签id找到标签名赋值给name
            LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.select(Tag::getTagName).eq(Tag::getId,condition.getTagId());
            Tag tag = tagDao.selectOne(tagWrapper);
            name = tag.getTagName();
        }

        ArticlePreviewListDTO articlePreviewListDTO = ArticlePreviewListDTO
            .builder()
            .articlePreviewDTOList(articlePreviewDTOList)
            .name(name)
            .build();
        return articlePreviewListDTO;
    }

    /**
     * 搜索文章
     *
     * @param condition 条件
     * @return 文章列表
     */
    @Override
    public List<ArticleSearchDTO> listArticlesBySearch(ConditionVO condition) {

        //todo 策略模式搜索文章
        List<ArticleSearchDTO> articleSearchDTOList = searchStrategyContext.executeSearchStrategy(condition.getKeywords());

        return articleSearchDTOList;
    }

    /**
     * 后台根据id查看文章--》修改文章需要先根据id获取该文章的数据
     *根据id从文章表获取文章信息---》赋值属性到VO--》tagNameList、categoryName还没赋值
     *categoryName： 在表中只有id--》将id变成name->根据分类id到分类表查询该分类--》获取该分类的名
     *tagNameList：根据文章id获取标签名集合，左连接--》文章标签表的标签id和标签表的id相同的标签名查询出来
     * @param articleId 文章id
     * @return 文章列表
     */
    @Override
    public ArticleVO getArticleBackById(Integer articleId) {
        Article article = articleDao.selectById(articleId);
        ArticleVO articleVO = BeanCopyUtils.copyObject(article, ArticleVO.class);
        Category category = categoryDao.selectById(article.getCategoryId());
        if (category !=null) {
            articleVO.setCategoryName(category.getCategoryName());
        }
        //根据文章id获取标签名集合，左连接--》文章标签表的标签id和标签表的id相同的标签名查询出来
        List<String> tagNameByList = tagDao.listTagNameByArticleId(articleId);
        articleVO.setTagNameList(tagNameByList);
        return articleVO;
    }

    /**
     * 更新文章浏览量
     * 根据文章id查询文章即用户跳转到具体文章时浏览量+1
     *
     * @param articleId 文章id
     */
    public void updateArticleViewsCount(Integer articleId) {

        // todo redis文章浏览量+1，redisTemplate.opsForZSet().incrementScore(key, value, score);
        redisService.zIncr(ARTICLE_VIEWS_COUNT, articleId, 1D);

        // 判断是否第一次访问，增加浏览量
        // Set<Integer> articleSet = CommonUtils.castSet(Optional.ofNullable(session.getAttribute(ARTICLE_SET)).orElseGet(HashSet::new), Integer.class);
        // if (!articleSet.contains(articleId)) {
        //     articleSet.add(articleId);
        //     session.setAttribute(ARTICLE_SET, articleSet);
        //     // todo redis文章浏览量+1，redisTemplate.opsForZSet().incrementScore(key, value, score);
        //     redisService.zIncr(ARTICLE_VIEWS_COUNT, articleId, 1D);
        // }
    }


    /**
     * 查询文章排行
     *将articleMap遍历每个的属性赋值倒序排列--》转换成articleRankDTOList集合
     * @param articleMap 文章信息
     * @return {@link List<ArticleRankDTO>} 文章排行
     */
    private List<ArticleRecommendDTO> listhotArticleRank(Map<Object, Double> articleMap) {
        // 创建动态数组
        List<Integer> articleIdList = new ArrayList<>(articleMap.size());
        //ARTICLE_VIEWS_COUNT存在redis的key为文章id--》遍历map获取key--》提取所有文章id--》添加到集合articleIdList中
        articleMap.forEach((key, value) -> articleIdList.add((Integer) key));
        // 查询文章信息
        return articleDao.selectList(new LambdaQueryWrapper<Article>()
            .select(Article::getId, Article::getArticleTitle,Article::getArticleCover,Article::getCreateTime)
            .eq(Article::getStatus, PUBLIC.getStatus())
            .eq(Article::getIsDelete, FALSE)
            //查询articleIdList的所有id范围内的所有文章id以及标题
            .in(Article::getId, articleIdList))
            //遍历map，查询数据库出来的文章标题以及浏览量赋值给每一个新建的ArticleRankDTO对象
            .stream().map(article -> ArticleRecommendDTO.builder()
                .id(article.getId())
                .articleTitle(article.getArticleTitle())
                .articleCover(article.getArticleCover())
                .createTime(article.getCreateTime())
                .viewsCount(articleMap.get(article.getId()).intValue())
                .build())
            //按浏览量倒序排序
            .sorted(Comparator.comparingInt(ArticleRecommendDTO::getViewsCount).reversed())
            //转换成list集合
            .collect(Collectors.toList());
    }

    /**
     * 前台根据id查看文章--》结果是dto对比实体类和dto有哪些不一样，赋值
     * 更新文章浏览量
     * 查询最新文章
     * 查询推荐文章
     * 上一篇、下一篇文章
     *
     * @param articleId 文章id
     * @return {@link ArticleDTO} 文章信息
     */
    @Override
    //todo 多线程 异步任务编排
    public ArticleDTO getArticleById(Integer articleId) {

        //根据id查询文章--》把实体类基础的信息先查询出来
        ArticleDTO articleDTO = articleDao.getArticleById(articleId);
        if (articleDTO.getId() == null){
            throw new BizException("文章不存在");
        }

        //todo 查询推荐文章RecommendArticleList
        CompletableFuture<List<ArticleRecommendDTO>> recommendArticleList = CompletableFuture.supplyAsync(() ->
            articleDao.listRecommendArticles(articleId));

        //查询最新的5篇文章--》状态公开、没有被删除，按修改时间、id排序，取5篇
        CompletableFuture<List<ArticleRecommendDTO>> newestArticleList = CompletableFuture.supplyAsync(() -> {
            LambdaQueryWrapper<Article> articleWrapper = new LambdaQueryWrapper<>();
            articleWrapper
                .select(Article::getId, Article::getArticleTitle, Article::getArticleCover, Article::getCreateTime)
                .eq(Article::getStatus, PUBLIC.getStatus())
                .eq(Article::getIsDelete, FALSE)
                .orderByDesc(Article::getCreateTime)
                .orderByDesc(Article::getId)
                .last("limit 5");
            List<Article> articles = articleDao.selectList(articleWrapper);
            List<ArticleRecommendDTO> articleRecommendDTOList = BeanCopyUtils.copyList(articles, ArticleRecommendDTO.class);
            return articleRecommendDTOList;
        });
        //热门文章
        CompletableFuture<List<ArticleRecommendDTO>> hotArticleList = CompletableFuture.supplyAsync(() ->{

            //文章浏览量排行,前五柱状图，articleRankDTOList，
            // 从redis中按score倒序获取浏览量前五的文章，和listArticleRank方法的倒序排列不重复，因为redis存的文章很多篇，只取redis中文章浏览量最多的5篇，而listArticleRank方法是在这5篇的基础上
            Map<Object, Double> articleMap = redisService.zReverseRangeWithScore(ARTICLE_VIEWS_COUNT, 0, 4);
            if (CollectionUtils.isNotEmpty(articleMap))
            {
                //调用该类的查询文章排行方法，将articleMap遍历每个的属性赋值倒序排列--》转换成articleRankDTOList集合
                List<ArticleRecommendDTO> articleRankDTOList = listhotArticleRank(articleMap);
                List<ArticleRecommendDTO> articleHotDTOList = BeanCopyUtils.copyList(articleRankDTOList, ArticleRecommendDTO.class);
                return articleHotDTOList;
            }
            return null;
            }


        );
        try {
            //推荐文章
            articleDTO.setRecommendArticleList(recommendArticleList.get());
            //最新文章
            articleDTO.setNewestArticleList(newestArticleList.get());
            //热门文章
            articleDTO.setHotArticleList(hotArticleList.get());

        } catch (Exception e) {
            log.error(StrUtil.format("堆栈信息:{}", ExceptionUtil.stacktraceToString(e)));
        }

        // 查询上一篇，下一篇文章
        LambdaQueryWrapper<Article> lastWrapper = new LambdaQueryWrapper<>();
        lastWrapper
            .select(Article::getId, Article::getArticleTitle, Article::getArticleCover).eq(Article::getIsDelete, FALSE)
            .eq(Article::getStatus, PUBLIC.getStatus())
            //less than 按小于该文章id的文章排序，即前一个
            .lt(Article::getId, articleId)
            .orderByDesc(Article::getId)
            .last("limit 1");
        Article lastArticle = articleDao.selectOne(lastWrapper);
        ArticlePaginationDTO lastArticlePaginationDTO = BeanCopyUtils.copyObject(lastArticle, ArticlePaginationDTO.class);

        LambdaQueryWrapper<Article> nextWrapper = new LambdaQueryWrapper<>();
        nextWrapper
            .select(Article::getId, Article::getArticleTitle, Article::getArticleCover).eq(Article::getIsDelete, FALSE)
            .eq(Article::getStatus, PUBLIC.getStatus())
            //grate than 按大于该文章id的文章排序，即前一个
            .gt(Article::getId, articleId)
            .orderByDesc(Article::getId)
            .last("limit 1");
        Article nextArticle = articleDao.selectOne(nextWrapper);
        //赋值属性到文章上下篇dto
        ArticlePaginationDTO nextArticlePaginationDTO = BeanCopyUtils.copyObject(nextArticle, ArticlePaginationDTO.class);
        //上一篇
        articleDTO.setLastArticle(lastArticlePaginationDTO);
        //下一篇
        articleDTO.setNextArticle(nextArticlePaginationDTO);

        //如果文章存在，浏览量+1，从redis中更新浏览量+1
        updateArticleViewsCount(articleId);
        //todo 获取zset指定元素分数,redisTemplate.opsForZSet().score(key, value); 根据文章id获取该文章的浏览量
        Double score = redisService.zScore(ARTICLE_VIEWS_COUNT, articleId);
        if (score != null){
            //浏览量
            articleDTO.setViewsCount(score.intValue());
        }
        //todo 获取Hash结构中的属性,redisTemplate.opsForHash().get(key, hashKey);根据文章id获取点赞量
        Integer likeCount = (Integer) redisService.hGet(ARTICLE_LIKE_COUNT, articleId.toString());
        //点赞量
        articleDTO.setLikeCount(likeCount);
        return articleDTO;
    }

    /**
     * 点赞文章
     *
     * @param articleId 文章id
     */
    @Override
    public void saveArticleLike(Integer articleId) {
        //存在redis的key：article_user_like:1（用户id），记录某个用户点赞了哪些文章
        String articleLikeKey = ARTICLE_USER_LIKE + UserUtils.getLoginUser().getUserInfoId();
        //todo redisTemplate.opsForSet().isMember(key, value),是否为Set中的属性
        if ( ! redisService.sIsMember(articleLikeKey,articleId))
        {
            //没点过赞则可以点赞
            //todo 用户未点赞则增加该用户点赞的文章id，redisTemplate.opsForSet().add(key, values)，向Set结构中添加属性
            redisService.sAdd(articleLikeKey,articleId);
            //todo 文章点赞数加一，redisTemplate.opsForHash().increment(key点赞数, hashKey说说的id, 数量单位为1)，Hash结构中属性递增
            redisService.hIncr(ARTICLE_LIKE_COUNT,articleId.toString(),1L);
        }else{
            //已经点过赞-->取消点赞
            //todo 用户点过赞则删除该用户点赞的文章id，redisTemplate.opsForSet().remove(key, values)，删除Set结构中的属性
            redisService.sRemove(articleLikeKey,articleId);
            //todo 该文章点赞数减一redisTemplate.opsForHash().increment(key, hashKey, -delta)，Hash结构中属性递减
            redisService.hDecr(ARTICLE_LIKE_COUNT,articleId.toString(),1L);
        }
    }

    /**
     * 保存文章标签
     * 新增文章--》添加标签--》遍历标签
     * 修改文章--》则先删除文章所有标签--》重新添加标签--》遍历标签
     *      如果标签存在--》直接添加到文章标签表
     *      如果标签不存在--》标签表新增标签--》添加到文章标签表
     *
     *
     * @param articleVO 文章信息
     */
    private void saveArticleTag(ArticleVO articleVO, Integer articleId){
        //文章id存在--》修改状态
        if (articleId != null){
            LambdaQueryWrapper<ArticleTag> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(ArticleTag::getArticleId,articleId);
            articleTagDao.delete(wrapper);
        }
            //新增文章--》拿到传入的所有标签名集合--》
            List<String> tagNameList = articleVO.getTagNameList();
            if (CollectionUtils.isNotEmpty(tagNameList)){
                LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
                tagWrapper.in(Tag::getTagName,tagNameList);
                // 查询标签表有的标签
                List<Tag> existTagList = tagDao.selectList(tagWrapper);
                //遍历拿到表中存在的所有标签名集合
                List<String> existTagNameList  = existTagList.stream().map(Tag::getTagName).collect(Collectors.toList());
                List<Integer> existTagIdList = existTagList.stream().map(Tag::getId).collect(Collectors.toList());
                //除去表中存在的标签，看看有没有剩下的在标签表中不存在的标签，如果有要把不存在的标签数据插入到标签表，再遍历新的标签集合将标签id添加到existTagIdList--》最后遍历existTagIdList集合根据标签id插入数据到文章标签表
                tagNameList.removeAll(existTagNameList);
                if (CollectionUtils.isNotEmpty(tagNameList)){
                    //遍历每个新的标签插入到标签表中
                    List<Tag> tagList = tagNameList.stream().map(tagName ->
                        Tag.builder()
                            .tagName(tagName)
                            .build())
                        .collect(Collectors.toList());
                    //插入到标签表中
                    tagService.saveBatch(tagList);
                    //这时候新的标签已经有id了，遍历每个新标签的id添加到existTagIdList
                    List<Integer> tagIdList = tagList.stream().map(Tag::getId).collect(Collectors.toList());
                    existTagIdList.addAll(tagIdList);
                }
                //最后一步，根据标签id集合添加数据到文章标签表
                List<ArticleTag> articleTagList = existTagIdList.stream().map(tagId ->
                    ArticleTag
                        .builder()
                        .articleId(articleId)
                        .tagId(tagId)
                        .build())
                    .collect(Collectors.toList());
                articleTagService.saveBatch(articleTagList);
            }
    }



    /**
     * 添加或修改文章
     * 没有封面添加默认封面
     * 标题不能重复
     * "categoryName": "",--》实体类为分类id--》根据名字找到id添加到文章表
     * "tagNameList": [],--》实体类没有，--》添加到文章标签表
     *
     * @param articleVO 文章信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateArticle(ArticleVO articleVO) {

        // for (int i = 0; i < 5000; i++) {
        //
        //     LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        //     wrapper.eq(Article::getArticleTitle,articleVO.getArticleTitle());
        //     // Article selectOne = articleDao.selectOne(wrapper);
        //     // //如果不是同一篇文章，标题又相同，抛出异常标题不能重复
        //     // if (selectOne != null && !selectOne.getId().equals(articleVO.getId())){
        //     //     articleVO.setArticleTitle(articleVO.getArticleTitle());
        //     //     throw new BizException("标题不能重复");
        //     // }
        //     //还差categoryName、tagNameList、UserId没有处理
        //     Article article = BeanCopyUtils.copyObject(articleVO, Article.class);
        //     //elasticsearchDao.save(BeanCopyUtils.copyObject(article, ArticleSearchDTO.class));
        //     article.setUserId(UserUtils.getLoginUser().getUserInfoId());
        //     // todo 多线程 任务编排 查询博客配置信息
        //     CompletableFuture<WebsiteConfigVO> webConfig = CompletableFuture.supplyAsync(() -> blogInfoService.getWebsiteConfig());
        //     //如果封面为空，设置默认封面
        //     if (StrUtil.isBlank(article.getArticleCover())){
        //         try {
        //             article.setArticleCover(webConfig.get().getArticleCover());
        //         } catch (Exception e) {
        //             throw new BizException("设定默认文章封面失败");
        //         }
        //     }
        //     //"categoryName": "",--》实体类为分类id--》根据名字找到id添加到文章表
        //     LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
        //     categoryWrapper.select(Category::getId);
        //     categoryWrapper.eq(Category::getCategoryName,articleVO.getCategoryName());
        //     Category category = categoryDao.selectOne(categoryWrapper);
        //     //如果添加的分类找数据库中找不到要添加到数据库中,文章不是草稿的状态
        //     if (category == null && !articleVO.getStatus().equals(DRAFT.getStatus())){
        //         category = Category.builder().categoryName(articleVO.getCategoryName()).build();
        //         categoryDao.insert(category);
        //     }
        //     //如果不是草稿的状态，保存文章分类，如果是草稿，不需要设置分类，标签
        //     if (Objects.nonNull(category)) {
        //         article.setCategoryId(category.getId());
        //     }
        //
        //     this.saveOrUpdate(article);
        //     //保存文章标签"tagNameList": [],--》实体类没有--》添加到文章标签表
        //     //saveArticleTag(articleVO, article.getId());
        //
        //     //标签表找到对应的id
        //     List<String> tagNameList = articleVO.getTagNameList();
        //     LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
        //     tagWrapper.in(Tag::getTagName,tagNameList);
        //     // 查询标签表有的标签
        //     List<Tag> existTagList = tagDao.selectList(tagWrapper);
        //     //遍历拿到表中存在的所有标签名集合
        //     //List<String> existTagNameList  = existTagList.stream().map(Tag::getTagName).collect(Collectors.toList());
        //     List<Integer> existTagIdList = existTagList.stream().map(Tag::getId).collect(Collectors.toList());
        //     List<ArticleTag> articleTagList = existTagIdList.stream().map(tagId ->
        //         ArticleTag
        //             .builder()
        //             .articleId(article.getId())
        //             .tagId(tagId)
        //             .build())
        //         .collect(Collectors.toList());
        //     articleTagService.saveBatch(articleTagList);
        //
        //
        // }

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getArticleTitle,articleVO.getArticleTitle());
        // Article selectOne = articleDao.selectOne(wrapper);
        // //如果不是同一篇文章，标题又相同，抛出异常标题不能重复
        // if (selectOne != null && !selectOne.getId().equals(articleVO.getId())){
        //     throw new BizException("标题不能重复");
        // }
        //还差categoryName、tagNameList、UserId没有处理
        Article article = BeanCopyUtils.copyObject(articleVO, Article.class);
        //todo es手动同步
        //elasticsearchDao.save(BeanCopyUtils.copyObject(article, ArticleSearchDTO.class));
        article.setUserId(UserUtils.getLoginUser().getUserInfoId());
        // todo 多线程 任务编排 查询博客配置信息
        CompletableFuture<WebsiteConfigVO> webConfig = CompletableFuture.supplyAsync(() -> blogInfoService.getWebsiteConfig());
        //如果封面为空，设置默认封面
        if (StrUtil.isBlank(article.getArticleCover())){
            try {
                article.setArticleCover(webConfig.get().getArticleCover());
            } catch (Exception e) {
                throw new BizException("设定默认文章封面失败");
            }
        }
        //"categoryName": "",--》实体类为分类id--》根据名字找到id添加到文章表
        LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
        categoryWrapper.select(Category::getId);
        categoryWrapper.eq(Category::getCategoryName,articleVO.getCategoryName());
        Category category = categoryDao.selectOne(categoryWrapper);
        //如果添加的分类找数据库中找不到要添加到数据库中,文章不是草稿的状态
        if (category == null && !articleVO.getStatus().equals(DRAFT.getStatus())){
            category = Category.builder().categoryName(articleVO.getCategoryName()).build();
            categoryDao.insert(category);
        }
        //如果不是草稿的状态，保存文章分类，如果是草稿，不需要设置分类，标签
        if (Objects.nonNull(category)) {
            article.setCategoryId(category.getId());
        }
        this.saveOrUpdate(article);
        //保存文章标签"tagNameList": [],--》实体类没有--》添加到文章标签表
        saveArticleTag(articleVO, article.getId());
        //importDataIntoES();

    }

    /**
     * 修改文章置顶
     *
     * @param articleTopVO 文章置顶信息
     */
    @Override
    public void updateArticleTop(ArticleTopVO articleTopVO) {
        //修改文章置顶状态，记得先赋值给id不然数据库不知道要给哪个文章置顶
        Article article = Article
            .builder()
            .id(articleTopVO.getId())
            .isTop(articleTopVO.getIsTop())
            .build();
        articleDao.updateById(article);
    }

    /**
     * 删除或恢复文章
     *
     * @param deleteVO 逻辑删除对象
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateArticleDelete(DeleteVO deleteVO) {
        List<Article> articleList = deleteVO.getIdList().stream().map(articleId ->
            Article.builder()
                .id(articleId)
                //给文章状态赋值
                .isDelete(deleteVO.getIsDelete())
                .build()
        ).collect(Collectors.toList());

        this.updateBatchById(articleList);
    }

    /**
     * 物理删除文章
     *
     * @param articleIdList 文章id集合
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteArticles(List<Integer> articleIdList) {
        LambdaQueryWrapper<ArticleTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ArticleTag::getArticleId,articleIdList);
        // 删除文章标签关联
        articleTagDao.delete(wrapper);
        //彻底从数据库删除文章
        articleDao.deleteBatchIds(articleIdList);

    }

    /**
     * 导出文章
     *
     * @param articleIdList 文章id列表
     * @return {@link List}<{@link String}> 文件地址
     */
    @Override
    public List<String> exportArticles(List<Integer> articleIdList) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Article::getArticleTitle,Article::getArticleContent);
        wrapper.in(Article::getId,articleIdList);
        //查询要导出的文章标题内容
        List<Article> articleList = articleDao.selectList(wrapper);
        // 写入文件并上传
        List<String> urlList = new ArrayList<>();
        //遍历每篇文章，
        for (Article article : articleList) {
            //ByteArrayInputStream字节数组输入流--》文章内容字节流
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(article.getArticleContent().getBytes())) {
                //uploadStrategyContext上传策略，FileExtEnum.MD.getExtName()：markdown文件扩展名，路径
                String url = uploadStrategyContext.executeUploadStrategy(article.getArticleTitle() + FileExtEnum.MD.getExtName(), inputStream, FilePathEnum.MD.getPath());
                //将导出文章的url添加到集合
                urlList.add(url);
            } catch (Exception e) {
                log.error(StrUtil.format("导出文章失败,堆栈:{}", ExceptionUtil.stacktraceToString(e)));
                throw new BizException("导出文章失败");
            }
        }
        return urlList;
    }
    
     public static void main(String[] args) {
         for (int i = 0; i < 5; i++) {
             System.out.println(i);
         }
         }
}
