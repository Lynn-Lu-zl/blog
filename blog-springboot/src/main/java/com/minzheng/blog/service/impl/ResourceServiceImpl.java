package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.ResourceDao;
import com.minzheng.blog.dao.RoleResourceDao;
import com.minzheng.blog.dto.LabelOptionDTO;
import com.minzheng.blog.dto.ResourceDTO;
import com.minzheng.blog.entity.Resource;
import com.minzheng.blog.entity.RoleResource;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import com.minzheng.blog.service.ResourceService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.ResourceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.FALSE;

/**
 * 资源服务
 *
 */
@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceDao, Resource> implements ResourceService {
    @Autowired
    private ResourceDao resourceDao;
    @Autowired
    private RoleResourceDao roleResourceDao;
    @Autowired
    private FilterInvocationSecurityMetadataSourceImpl filterInvocationSecurityMetadataSource;

    /**
     * 添加或修改资源
     *
     * @param resourceVO 资源对象
     */
    @Transactional
    @Override
    public void saveOrUpdateResource(ResourceVO resourceVO) {
        Resource resource = BeanCopyUtils.copyObject(resourceVO, Resource.class);
        this.saveOrUpdate(resource);
        //todo 重新加载角色资源信息,clearDataSource清空接口角色信息
        filterInvocationSecurityMetadataSource.clearDataSource();
    }

    /***
     * 删除资源
     *
     * 根据资源id--》表tb_role_resource--》查询是否有角色关联-->传入的resource id在表中是否有对应的role id
     * 有关联--》抛出异常不能删除
     * 无关联--》删除父类资源以及对应的子类资源
     * @param resourceId 资源id
     */
    @Override
    public void deleteResource(Integer resourceId) {
        LambdaQueryWrapper<RoleResource> wrapper = new LambdaQueryWrapper<>();
        //传入的resource id在表中是否有对应的role id
        wrapper.eq(RoleResource::getResourceId,resourceId);
        Integer count = roleResourceDao.selectCount(wrapper);
        //有关联--》抛出异常不能删除
        if (count > 0)
        {
            throw  new BizException("删除失败，有角色关联该资源，请先取消角色和该资源的绑定");
        }
        //无关联，找到该资源下绑定的子类资源--》查询Resource的ParentId和传入的resourceId相同的组成集合--》集合：子资源+自己一起删除
        LambdaQueryWrapper<Resource> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.select(Resource::getId);
        resourceWrapper.eq(Resource::getParentId,resourceId);
        //找出子类资源还得加上自己
        List<Resource> resourceList = resourceDao.selectList(resourceWrapper);
        //因为resourceList是Resource类型，而自己只有resourceId不是Resource类型--》stream根据将List<Resource>转成List<Integer>类型
        // List<Integer> resourceIdList = new ArrayList<>();
        // for (Resource resource : resourceList) {
        //     Integer id = resource.getId();
        //     resourceIdList.add(id);
        // }

        //使用Lambda表达式将类型转换为Integer类型，也可以写成匿名内部类的形式.map(resource->resource.getId())，相当于遍历resourceList集合中的元素取出每个元素的id添加到一个新的集合resourceIdList
        List<Integer> resourceIdList = resourceList
            .stream()
            .map(Resource::getId)
            .collect(Collectors.toList());
        //集合：子资源+自己一起删除，转换后就可以加上自己一起删除了
        resourceIdList.add(resourceId);
        resourceDao.deleteBatchIds(resourceIdList);
    }



    /**
     * 获取所有资源的父级模块
     *
     * @param resourceList 资源列表
     * @return 资源模块列表
     */
    private List<Resource> listResourceModule(List<Resource> resourceList)
    {
        //遍历资源列表--》过滤条件：只要父类id为空的资源--》添加到一个全是父类资源的新集合
        List<Resource> listResourceModule = resourceList.stream()
            .filter(resource -> Objects.isNull(resource.getParentId()))
            .collect(Collectors.toList());
        return listResourceModule;
    }

    /**
     * 获取父级模块下的所有子级资源
     *
     * @param resourceList 资源列表
     * @return 模块资源 使用Map<Integer, List<Resource>>集合--》子级资源不止一个
     * 因为map有key，value--》key为父级id，value为父级id下所有的子级资源
     */
    private Map<Integer, List<Resource>> listResourceChildren(List<Resource> resourceList)
    {
        //遍历资源列表--》过滤条件：只要父类id不为空的资源--》添加到一个全是子类资源的新集合listResourceChildren--》按照父类id分组--》相同父类id的分为一组
        Map<Integer, List<Resource>> listResourceChildren = resourceList
            .stream()
            .filter(resource -> Objects.nonNull(resource.getParentId()))
            //.collect(Collectors.groupingBy(resource -> resource.getParentId()));
            .collect(Collectors.groupingBy(Resource::getParentId));
        return listResourceChildren;
    }

    /**
     * 后台查看资源列表
     * admin/resources?keywords=
     *
     * @param conditionVO 查询条件，keywords
     * @return 资源列表ResourceDTO
     */
    @Override
    public List<ResourceDTO> listResources(ConditionVO conditionVO) {
        //如果关键词不为空则查询资源名对应的资源列表信息，为空则查询所有资源
        LambdaQueryWrapper<Resource> resourceWrapper = new LambdaQueryWrapper<>();
        resourceWrapper.like(StringUtils.isNotBlank(conditionVO.getKeywords()), Resource::getResourceName,conditionVO.getKeywords());
        List<Resource> resourceList = resourceDao.selectList(resourceWrapper);
        //根据资源列表获取所有资源的父级模块
        List<Resource> parentResourceList = listResourceModule(resourceList);
        //根据资源列表获取所有父级资源的子级资源
        Map<Integer, List<Resource>> childrenResourceMap = listResourceChildren(resourceList);
        // 绑定模块下的所有接口
        List<ResourceDTO> resourceDTOList = parentResourceList.stream().map(resource -> {
            ResourceDTO resourceDTO = BeanCopyUtils.copyObject(resource, ResourceDTO.class);
            List<ResourceDTO> childrenList = BeanCopyUtils.copyList(childrenResourceMap.get(resource.getId()), ResourceDTO.class);
            resourceDTO.setChildren(childrenList);
            //
            childrenResourceMap.remove(resource.getId());
            return resourceDTO;
        }).collect(Collectors.toList());
        //若还有资源未取出则拼接
        if (CollectionUtils.isNotEmpty(childrenResourceMap))
        {
            List<Resource> childrenList = new ArrayList<>();
            childrenResourceMap.values().forEach(childrenList::addAll);
            List<ResourceDTO> childrenDTOList = childrenList
                .stream()
                .map(resource -> BeanCopyUtils.copyObject(resource, ResourceDTO.class))
                .collect(Collectors.toList());
            resourceDTOList.addAll(childrenDTOList);
        }
        return resourceDTOList;

    }

    /**
     * 后台查看资源选项
     *
     * @return 资源选项
     */
    @Override
    public List<LabelOptionDTO> listResourceOption() {
        // 查询资源列表，不是匿名访问的资源
        List<Resource> resourceList = resourceDao.selectList(new LambdaQueryWrapper<Resource>()
            .select(Resource::getId, Resource::getResourceName, Resource::getParentId)
            .eq(Resource::getIsAnonymous, FALSE));
        // 根据资源列表获取所有资源的父级模块
        List<Resource> parentList = listResourceModule(resourceList);
        // 根据资源列表获取所有父级资源的子级资源
        Map<Integer, List<Resource>> childrenMap = listResourceChildren(resourceList);
        // 组装父子数据
        //遍历父类集合
        return parentList.stream().map(item -> {
            List<LabelOptionDTO> list = new ArrayList<>();
            //根据key即父类id取出所有的子类资源
            List<Resource> children = childrenMap.get(item.getId());
            //如果子类资源不为空，遍历每个子类资源转换成LabelOptionDTO集合list
            if (CollectionUtils.isNotEmpty(children)) {
                //
                list = children.stream()
                    .map(resource -> LabelOptionDTO.builder()
                        .id(resource.getId())
                        .label(resource.getResourceName())
                        .build())
                    .collect(Collectors.toList());
            }
            //
            return LabelOptionDTO.builder()
                .id(item.getId())
                .label(item.getResourceName())
                .children(list)
                .build();
        }).collect(Collectors.toList());
    }
}
