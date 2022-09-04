package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.constant.CommonConst;
import com.minzheng.blog.dao.RoleDao;
import com.minzheng.blog.dao.UserRoleDao;
import com.minzheng.blog.dto.RoleDTO;
import com.minzheng.blog.dto.UserRoleDTO;
import com.minzheng.blog.entity.Role;
import com.minzheng.blog.entity.RoleMenu;
import com.minzheng.blog.entity.RoleResource;
import com.minzheng.blog.entity.UserRole;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import com.minzheng.blog.service.RoleMenuService;
import com.minzheng.blog.service.RoleResourceService;
import com.minzheng.blog.service.RoleService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.PageResult;
import com.minzheng.blog.vo.RoleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色服务
 *
 */
@Service
public class RoleServiceImpl extends ServiceImpl<RoleDao, Role> implements RoleService {
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private RoleResourceService roleResourceService;
    @Autowired
    private RoleMenuService roleMenuService;
    @Autowired
    private UserRoleDao userRoleDao;
    @Autowired
    private FilterInvocationSecurityMetadataSourceImpl filterInvocationSecurityMetadataSource;

    /**
     * 获取用户角色选项
     *
     * @return 角色
     */
    @Override
    public List<UserRoleDTO> listUserRoles() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        //查询所有的角色id，角色名
        wrapper.select(Role::getId,Role::getRoleName);
        //组成集合
        List<Role> roleList = roleDao.selectList(wrapper);
        List<UserRoleDTO> userRoleDTOList = BeanCopyUtils.copyList(roleList, UserRoleDTO.class);
        return userRoleDTOList;
    }

    /**
     * 查询角色列表
     *
     * @param conditionVO 条件
     * @return 角色列表
     */
    @Override
    public PageResult<RoleDTO> listRoles(ConditionVO conditionVO) {
        List<RoleDTO> roleDTOList = roleDao.listRoles(PageUtils.getLimitCurrent(), PageUtils.getSize(), conditionVO);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        //如果输入的关键字不为空则返回根据模糊查询的角色数量，为空则返回所有的角色数量
        wrapper.like(StringUtils.isNotBlank(conditionVO.getKeywords()),Role::getRoleName,conditionVO.getKeywords());
        Integer count = roleDao.selectCount(wrapper);
        return new PageResult<>(roleDTOList,count);
    }

    /**
     * 保存或更新角色
     *
     * 新增：角色名+角色标签+角色菜单权限roleMenu+角色资源权限roleResource
     * @param roleVO 角色
     */
    @Override
    public void saveOrUpdateRole(RoleVO roleVO) {
        //非空判断，如果新增的角色名已经存在抛出异常
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Role::getId).eq(Role::getRoleName,roleVO.getRoleName());
        Role existRole = roleDao.selectOne(wrapper);
        if (existRole != null)
        {
            throw new BizException("角色名已存在");
        }
        // 保存或更新角色信息
        Role role = Role.builder()
            .id(roleVO.getId())
            .roleName(roleVO.getRoleName())
            .roleLabel(roleVO.getRoleLabel())
            .isDisable(CommonConst.FALSE)
            .build();
        this.saveOrUpdate(role);

        // 更新角色资源关系，RoleResource先删除后增加--》Security重新加载角色资源信息
        if (Objects.nonNull(roleVO.getResourceIdList())){
            if (Objects.nonNull(roleVO.getId())){
                //删除原来的
                LambdaQueryWrapper<RoleResource> roleResourceWrapper = new LambdaQueryWrapper<>();
                roleResourceWrapper.eq(RoleResource::getRoleId,roleVO.getId());
                roleResourceService.remove(roleResourceWrapper);
            }
            //新增的赋值
            List<RoleResource> roleResourceList = roleVO.getResourceIdList()
                .stream()
                .map(resourceId -> RoleResource
                    .builder()
                    .roleId(role.getId())
                    .resourceId(resourceId)
                    .build())
                .collect(Collectors.toList());
            //插入数据
            roleResourceService.saveBatch(roleResourceList);
            //Security重新加载角色资源信息
            filterInvocationSecurityMetadataSource.clearDataSource();

        }

        //更新角色菜单关系，RoleMenu先删除后增加--》
        if (Objects.nonNull(roleVO.getMenuIdList())) {
            if (Objects.nonNull(roleVO.getId())) {
                roleMenuService.remove(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleVO.getId()));
            }
            List<RoleMenu> roleMenuList = roleVO.getMenuIdList().stream()
                .map(menuId -> RoleMenu.builder()
                    .roleId(role.getId())
                    .menuId(menuId)
                    .build())
                .collect(Collectors.toList());
            roleMenuService.saveBatch(roleMenuList);
        }
    }

    /**
     * 删除角色
     *
     * @param roleIdList 角色id列表
     */
    @Override
    public void deleteRoles(List<Integer> roleIdList) {
        // 判断角色下是否有用户
        Integer count = userRoleDao.selectCount(new LambdaQueryWrapper<UserRole>().in(UserRole::getRoleId, roleIdList));
        if (count > 0)
        {
            throw new BizException("该角色下存在用户,无法删除，请先取消角色和用户的绑定");
        }else {
            roleDao.deleteBatchIds(roleIdList);
        }
    }
}
