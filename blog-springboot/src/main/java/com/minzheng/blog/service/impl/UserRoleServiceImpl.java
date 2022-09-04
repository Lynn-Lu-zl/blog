package com.minzheng.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.UserRoleDao;
import com.minzheng.blog.entity.UserRole;
import com.minzheng.blog.service.UserRoleService;
import org.springframework.stereotype.Service;


/**
 * 用户角色服务

 */
@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleDao, UserRole> implements UserRoleService  {
}
