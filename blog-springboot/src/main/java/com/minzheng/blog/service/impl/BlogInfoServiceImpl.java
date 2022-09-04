package com.minzheng.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.minzheng.blog.dao.*;
import com.minzheng.blog.dto.*;
import com.minzheng.blog.entity.Article;
import com.minzheng.blog.entity.Tag;
import com.minzheng.blog.entity.WebsiteConfig;
import com.minzheng.blog.service.BlogInfoService;
import com.minzheng.blog.service.PageService;
import com.minzheng.blog.service.RedisService;
import com.minzheng.blog.service.UniqueViewService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.IpUtils;
import com.minzheng.blog.vo.BlogInfoVO;
import com.minzheng.blog.vo.PageVO;
import com.minzheng.blog.vo.WebsiteConfigVO;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.*;
import static com.minzheng.blog.constant.RedisPrefixConst.*;
import static com.minzheng.blog.enums.PhotoAlbumStatusEnum.PUBLIC;

/**
 * 博客信息服务
 *
 */
@Service
public class BlogInfoServiceImpl implements BlogInfoService {
    @Autowired
    private UserInfoDao userInfoDao;
    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private CategoryDao categoryDao;
    @Autowired
    private TagDao tagDao;
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private UniqueViewService uniqueViewService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private WebsiteConfigDao websiteConfigDao;
    @Resource
    private HttpServletRequest request;
    @Autowired
    private PageService pageService;

    /**
     * 获取网站配置
     * 从redis看有没有缓存
     * 没有--》从数据库查询
     * 有--》redis的缓存
     *
     * @return {@link WebsiteConfigVO} 网站配置
     */
    @Override
    public WebsiteConfigVO getWebsiteConfig() {
        WebsiteConfigVO websiteConfigVO;
        //从redis的缓存获取
        Object websiteConfig = redisService.get(WEBSITE_CONFIG);
        if (Objects.nonNull(websiteConfig))
        {
            //将存在redis的JSON字符串解析成java对象
            websiteConfigVO = JSON.parseObject(websiteConfig.toString(), WebsiteConfigVO.class);
        }
        else
            //没有则从数据库中查询
        {

            String config = websiteConfigDao.selectById(DEFAULT_CONFIG_ID).getConfig();
            //将存在mysql的JSON字符串解析成java对象
            websiteConfigVO = JSON.parseObject(config, WebsiteConfigVO.class);
            //存入redis缓存
            redisService.set(WEBSITE_CONFIG, config);

        }

        return websiteConfigVO;
    }
    /**
     * 获取前台首页数据
     *
     * @return 博客首页信息
     */
    @Override
    public BlogHomeInfoDTO getBlogHomeInfo() {

        LambdaQueryWrapper<Article> articleLambdaQueryWrapper = new LambdaQueryWrapper<>();
        //文章状态 1.公开+未删除
        articleLambdaQueryWrapper.eq(Article::getStatus,PUBLIC.getStatus()).eq(Article::getIsDelete, FALSE);
        //文章数量
        Integer articleCount = articleDao.selectCount(articleLambdaQueryWrapper);
        //分类数量
        Integer categoryCount = categoryDao.selectCount(null);
        //标签数量
        Integer tagCount = tagDao.selectCount(null);
        //目录页面列表,归档、首页、分类等等，动态获取目录列表，先从redis中查询缓存，没有则从数据库查
        List<PageVO> pageList = pageService.listPages();
        //网站访问量，从redis中获取
        Object count = redisService.get(BLOG_VIEWS_COUNT);
        //todo 非空效验，如果访问量为空设置为0
        String viewsCount = Optional.ofNullable(count).orElse(0).toString();

        //网站配置，调用该类写的方法
        WebsiteConfigVO websiteConfig = this.getWebsiteConfig();

        //赋值
        BlogHomeInfoDTO blogHomeInfoDTO = BlogHomeInfoDTO
            .builder()
            .articleCount(articleCount)
            .categoryCount(categoryCount)
            .pageList(pageList)
            .tagCount(tagCount)
            .viewsCount(viewsCount)
            .websiteConfig(websiteConfig)
            .build();

        return blogHomeInfoDTO;
    }

    /**
     * 查询文章排行
     *将articleMap遍历每个的属性赋值倒序排列--》转换成articleRankDTOList集合
     * @param articleMap 文章信息
     * @return {@link List<ArticleRankDTO>} 文章排行
     */
    private List<ArticleRankDTO> listArticleRank(Map<Object, Double> articleMap) {
        // 创建动态数组
        List<Integer> articleIdList = new ArrayList<>(articleMap.size());
        //ARTICLE_VIEWS_COUNT存在redis的key为文章id--》遍历map获取key--》提取所有文章id--》添加到集合articleIdList中
        articleMap.forEach((key, value) -> articleIdList.add((Integer) key));
        // 查询文章信息
        return articleDao.selectList(new LambdaQueryWrapper<Article>()
            .select(Article::getId, Article::getArticleTitle)
            //查询articleIdList的所有id范围内的所有文章id以及标题
            .in(Article::getId, articleIdList))
            //遍历map，查询数据库出来的文章标题以及浏览量赋值给每一个新建的ArticleRankDTO对象
            .stream().map(article -> ArticleRankDTO.builder()
                .articleTitle(article.getArticleTitle())
                .viewsCount(articleMap.get(article.getId()).intValue())
                .build())
            //按浏览量倒序排序
            .sorted(Comparator.comparingInt(ArticleRankDTO::getViewsCount).reversed())
            //转换成list集合
            .collect(Collectors.toList());
    }

    /**
     * 获取后台首页数据
     *
     * @return 博客后台信息
     */
    @Override
    public BlogBackInfoDTO getBlogBackInfo() {

        LambdaQueryWrapper<Article> articleLambdaQueryWrapper = new LambdaQueryWrapper<>();
        //文章状态 1.公开+未删除
        articleLambdaQueryWrapper.eq(Article::getStatus,PUBLIC.getStatus()).eq(Article::getIsDelete, FALSE);
        //文章数量articleCount
        Integer articleCount = articleDao.selectCount(articleLambdaQueryWrapper);

        //文章贡献统计列表，按时间像github的贡献量，articleStatisticsList
        List<ArticleStatisticsDTO> articleStatisticsList = articleDao.listArticleStatistics();
        //文章分类统计，饼状图categoryDTOList
        List<CategoryDTO> categoryDTOList = categoryDao.listCategoryDTO();

        //留言量，数值messageCount
        Integer messageCount = messageDao.selectCount(null);

        //文章标签统计，浮动的标签名称，tagDTOList
        List<Tag> tagList = tagDao.selectList(null);
        List<TagDTO> tagDTOList = BeanCopyUtils.copyList(tagList, TagDTO.class);


        //一周用户访问量集合,uniqueViewDTOList
        List<UniqueViewDTO> uniqueViewDTOList = uniqueViewService.listUniqueViews();

        //用户数量
        Integer userCount = userInfoDao.selectCount(null);

        //网站访问量，从redis中获取
        Object count = redisService.get(BLOG_VIEWS_COUNT);
        //非空效验，如果访问量为空设置为0
        Integer viewsCount = (Integer) Optional.ofNullable(count).orElse(0);

        BlogBackInfoDTO blogBackInfoDTO = BlogBackInfoDTO
            .builder()
            .articleCount(articleCount)
            .articleStatisticsList(articleStatisticsList)
            .categoryDTOList(categoryDTOList)
            .messageCount(messageCount)
            .tagDTOList(tagDTOList)
            .uniqueViewDTOList(uniqueViewDTOList)
            .userCount(userCount)
            .viewsCount(viewsCount)
            .build();

        //文章浏览量排行,前五柱状图，articleRankDTOList，
        // 从redis中按score倒序获取浏览量前五的文章，和listArticleRank方法的倒序排列不重复，因为redis存的文章很多篇，只取redis中文章浏览量最多的5篇，而listArticleRank方法是在这5篇的基础上
        Map<Object, Double> articleMap = redisService.zReverseRangeWithScore(ARTICLE_VIEWS_COUNT, 0, 4);
        if (CollectionUtils.isNotEmpty(articleMap))
        {
            //调用该类的查询文章排行方法，将articleMap遍历每个的属性赋值倒序排列--》转换成articleRankDTOList集合
            List<ArticleRankDTO> articleRankDTOList = listArticleRank(articleMap);
            blogBackInfoDTO.setArticleRankDTOList(articleRankDTOList);
        }

        return blogBackInfoDTO;
    }

    /**
     * 保存或更新网站配置
     * 从redis中删除缓存
     *
     * @param websiteConfigVO 网站配置
     */
    @Transactional
    @Override
    public void updateWebsiteConfig(WebsiteConfigVO websiteConfigVO) {
        // 修改网站配置
        // UPDATE tb_website_config SET config=?, update_time=? WHERE id=?
        WebsiteConfig websiteConfig = WebsiteConfig.builder()
            .id(1)
            //数据库中存储的config是JSON格式，所以要将java对象转换成JSON字符串
            .config(JSON.toJSONString(websiteConfigVO))
            .build();
        websiteConfigDao.updateById(websiteConfig);
        redisService.del(WEBSITE_CONFIG);
    }

    /**
     * 获取关于我内容
     * 从redis中取
     *
     * @return 关于我内容
     */
    @Override
    public String getAbout() {
        Object value = redisService.get(ABOUT);//从redis中获取关于我信息
        //没有则默认返回空字符串
        return Objects.nonNull(value) ? value.toString() : "";
    }

    /**
     * 保存/修改关于我内容
     * 存储到redis中
     *
     * @param blogInfoVO 博客信息
     */
    @Override
    public void updateAbout(BlogInfoVO blogInfoVO) {
        redisService.set(ABOUT,blogInfoVO.getAboutContent());
    }

    /**
     * 上传访客信息
     */
    @Override
    public void report() {

        // 获取ip
        String ipAddress = IpUtils.getIpAddress(request);
        // 获取访问设备
        UserAgent userAgent = IpUtils.getUserAgent(request);
        Browser browser = userAgent.getBrowser();
        OperatingSystem operatingSystem = userAgent.getOperatingSystem();
        // 生成唯一用户标识
        String uuid = ipAddress + browser.getName() + operatingSystem.getName();
        String md5 = DigestUtils.md5DigestAsHex(uuid.getBytes());
        //UNIQUE_VISITOR 访客
        // 根据存入redis的访客数据是否存在判断该用户是否已经访问过
        //todo redisTemplate.opsForSet().isMember(key, value)，是否为Set中的属性
        if (!redisService.sIsMember(UNIQUE_VISITOR, md5)) {
            // 统计游客地域分布
            String ipSource = IpUtils.getIpSource(ipAddress);
            if (StringUtils.isNotBlank(ipSource)) {
                ipSource = ipSource.substring(0, 2)
                    .replaceAll(PROVINCE, "")
                    .replaceAll(CITY, "");
                //VISITOR_AREA 访客地区
                // todo redisTemplate.opsForHash().increment(key, hashKey, delta)，Hash结构中属性递增
                redisService.hIncr(VISITOR_AREA, ipSource, 1L);
            } else {
                //未知地区
                redisService.hIncr(VISITOR_AREA, UNKNOWN, 1L);
            }
            // 访问量+1
            //todo redisTemplate.opsForValue().increment(key, delta)，递增+1
            redisService.incr(BLOG_VIEWS_COUNT, 1);
            // 保存唯一标识
            //todo redisTemplate.opsForSet().add(key, values)，向Set结构中添加属性
            //value="6d4ae7d2925f1bb81f5f14f858a9b924"
            redisService.sAdd(UNIQUE_VISITOR, md5);
        }
    }
}
