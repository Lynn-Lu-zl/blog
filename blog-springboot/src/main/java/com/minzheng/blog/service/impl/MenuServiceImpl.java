package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.MenuDao;
import com.minzheng.blog.dao.RoleMenuDao;
import com.minzheng.blog.dto.LabelOptionDTO;
import com.minzheng.blog.dto.MenuDTO;
import com.minzheng.blog.dto.UserMenuDTO;
import com.minzheng.blog.entity.Menu;
import com.minzheng.blog.entity.RoleMenu;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.service.MenuService;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.UserUtils;
import com.minzheng.blog.vo.ConditionVO;
import com.minzheng.blog.vo.MenuVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.*;


/**
 * 菜单服务
 *
 */
@Service
public class MenuServiceImpl extends ServiceImpl<MenuDao, Menu> implements MenuService {
    @Autowired
    private MenuDao menuDao;
    @Autowired
    private RoleMenuDao roleMenuDao;

    /**
     * 查看菜单列表
     *
     * @param conditionVO 条件
     * @return 菜单列表
     */
    @Override
    public List<MenuDTO> listMenus(ConditionVO conditionVO) {
        // 查询菜单数据
        List<Menu> menuList = menuDao.selectList(new LambdaQueryWrapper<Menu>()
            .like(StringUtils.isNotBlank(conditionVO.getKeywords()), Menu::getName, conditionVO.getKeywords()));
        // 获取目录列表
        List<Menu> catalogList = listCatalog(menuList);
        // 获取目录下的子菜单
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menuList);
        // 组装目录菜单数据
        List<MenuDTO> menuDTOList = catalogList.stream().map(item -> {
            MenuDTO menuDTO = BeanCopyUtils.copyObject(item, MenuDTO.class);
            // 获取目录下的菜单排序
            List<MenuDTO> list = BeanCopyUtils.copyList(childrenMap.get(item.getId()), MenuDTO.class).stream()
                .sorted(Comparator.comparing(MenuDTO::getOrderNum))
                .collect(Collectors.toList());
            menuDTO.setChildren(list);
            childrenMap.remove(item.getId());
            return menuDTO;
        }).sorted(Comparator.comparing(MenuDTO::getOrderNum)).collect(Collectors.toList());
        // 若还有菜单未取出则拼接
        if (CollectionUtils.isNotEmpty(childrenMap)) {
            List<Menu> childrenList = new ArrayList<>();
            childrenMap.values().forEach(childrenList::addAll);
            List<MenuDTO> childrenDTOList = childrenList.stream()
                .map(item -> BeanCopyUtils.copyObject(item, MenuDTO.class))
                .sorted(Comparator.comparing(MenuDTO::getOrderNum))
                .collect(Collectors.toList());
            menuDTOList.addAll(childrenDTOList);
        }
        return menuDTOList;
    }

    /**
     * 新增或修改菜单
     *
     * @param menuVO 菜单信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateMenu(MenuVO menuVO) {
        Menu menu = BeanCopyUtils.copyObject(menuVO, Menu.class);
        this.saveOrUpdate(menu);
    }

    /**
     * 删除菜单
     * 菜单和角色是否有
     *  有关联--》RoleMenu--》无法删除抛出异常
     *  无关联--》
     *
     *
     * @param menuId 菜单id
     */
    @Transactional
    @Override
    public void deleteMenu(Integer menuId) {
        LambdaQueryWrapper<RoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleMenu::getMenuId,menuId);
        Integer count = roleMenuDao.selectCount(wrapper);
        if (count > 0){
            throw  new BizException("菜单下有角色关联，无法删除，请先取消角色和菜单的绑定");
        }
        else {
            LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.select(Menu::getId).eq(Menu::getParentId,menuId);
            List<Menu> menuList = menuDao.selectList(queryWrapper);
            List<Integer> list = menuList.stream().map(Menu::getId).collect(Collectors.toList());
            list.add(menuId);
            menuDao.deleteBatchIds(list);
        }

    }

    /**
     * 查看角色菜单选项
     *
     * @return 角色菜单选项
     */
    @Override
    public List<LabelOptionDTO> listMenuOptions() {
        // 查询菜单数据
        List<Menu> menuList = menuDao.selectList(new LambdaQueryWrapper<Menu>()
            .select(Menu::getId, Menu::getName, Menu::getParentId, Menu::getOrderNum));
        // 获取父级目录列表
        List<Menu> catalogList = listCatalog(menuList);
        // 获取父级目录下的子菜单
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menuList);
        // 组装目录菜单数据
        return catalogList.stream().map(item -> {
            // 获取目录下的菜单排序
            List<LabelOptionDTO> list = new ArrayList<>();
            List<Menu> children = childrenMap.get(item.getId());
            if (CollectionUtils.isNotEmpty(children)) {
                list = children.stream()
                    .sorted(Comparator.comparing(Menu::getOrderNum))
                    .map(menu -> LabelOptionDTO.builder()
                        .id(menu.getId())
                        .label(menu.getName())
                        .build())
                    .collect(Collectors.toList());
            }
            return LabelOptionDTO.builder()
                .id(item.getId())
                .label(item.getName())
                .children(list)
                .build();
        }).collect(Collectors.toList());
    }

    /**
     * 遍历菜单--》父级id为空--》获取父级目录
     *
     * @param menuList 菜单列表
     * @return 目录列表
     */
    private List<Menu> listCatalog(List<Menu> menuList) {
        return menuList.stream()
            .filter(item -> Objects.isNull(item.getParentId()))
            .sorted(Comparator.comparing(Menu::getOrderNum))
            .collect(Collectors.toList());
    }

    /**
     *父级id不为空--》子级菜单
     *
     * @param menuList 菜单列表
     * @return 目录下的菜单列表
     */
    private Map<Integer, List<Menu>> getMenuMap(List<Menu> menuList) {
        return menuList.stream()
            .filter(item -> Objects.nonNull(item.getParentId()))
            .collect(Collectors.groupingBy(Menu::getParentId));
    }

    /**
     * 转换用户菜单格式
     *遍历父类目录--》创建对象--》获取子菜单--》属性赋值
     * @param catalogList 目录
     * @param childrenMap 子菜单
     */
    private List<UserMenuDTO> convertUserMenuList(List<Menu> catalogList, Map<Integer, List<Menu>> childrenMap) {
        return catalogList.stream().map(item -> {
            // 创建对象
            UserMenuDTO userMenuDTO = new UserMenuDTO();
            List<UserMenuDTO> list = new ArrayList<>();
            // 获取目录下的子菜单
            List<Menu> children = childrenMap.get(item.getId());
            if (CollectionUtils.isNotEmpty(children)) {
                // 多级菜单处理
                userMenuDTO = BeanCopyUtils.copyObject(item, UserMenuDTO.class);
                list = children.stream()
                    .sorted(Comparator.comparing(Menu::getOrderNum))
                    .map(menu -> {
                        UserMenuDTO dto = BeanCopyUtils.copyObject(menu, UserMenuDTO.class);
                        dto.setHidden(menu.getIsHidden().equals(TRUE));
                        return dto;
                    })
                    .collect(Collectors.toList());
            } else {
                // 一级菜单处理
                userMenuDTO.setPath(item.getPath());
                userMenuDTO.setComponent(COMPONENT);
                list.add(UserMenuDTO.builder()
                    .path("")
                    .name(item.getName())
                    .icon(item.getIcon())
                    .component(item.getComponent())
                    .build());
            }
            userMenuDTO.setHidden(item.getIsHidden().equals(TRUE));
            userMenuDTO.setChildren(list);
            return userMenuDTO;
        }).collect(Collectors.toList());
    }

    /**
     * 查看用户菜单
     *1、用户user_id--》表user_role-》用户角色role_id
     * role id-->表role_menu-->menu_id
     * menu_id-->表menu-->当前用户菜单信息
     *
     * 2、父类目录菜单parent_id--》parent_id相同的子类菜单
     * 3、转换成用户菜单格式
     * @return 菜单列表
     */
    @Override
    public List<UserMenuDTO> listUserMenus() {

        //获取当前登录用户id
        Integer userInfoId = UserUtils.getLoginUser().getUserInfoId();
        //当前用户的菜单信息
        List<Menu> menuList = menuDao.listMenusByUserInfoId(userInfoId);
        //父类
        List<Menu> catalogList = listCatalog(menuList);
        //子类
        Map<Integer, List<Menu>> childrenMap = getMenuMap(menuList);
        //转换成用户菜单格式
        List<UserMenuDTO> userMenuDTO = convertUserMenuList(catalogList, childrenMap);
        return userMenuDTO;
    }
}
