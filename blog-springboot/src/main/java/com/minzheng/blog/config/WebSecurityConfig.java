package com.minzheng.blog.config;

import com.minzheng.blog.handler.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.session.HttpSessionEventPublisher;


/**
 * Security配置类
 * 继承WebSecurityConfigurerAdapter
 * 1、密码加密存储：SpringSecurity自带的BCryptPasswordEncoder加密方式
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    // @Autowired
    // private AuthenticationEntryPointImpl authenticationEntryPoint;
    // @Autowired
    // private AccessDeniedHandlerImpl accessDeniedHandler;
    // @Autowired
    // private AuthenticationSuccessHandlerImpl authenticationSuccessHandler;
    // @Autowired
    // private AuthenticationFailHandlerImpl authenticationFailHandler;
    // @Autowired
    // private LogoutSuccessHandlerImpl logoutSuccessHandler;
    //
    // @Bean
    // public FilterInvocationSecurityMetadataSource securityMetadataSource() {
    //     return new FilterInvocationSecurityMetadataSourceImpl();
    // }
    //
    // @Bean
    // public AccessDecisionManager accessDecisionManager() {
    //     return new AccessDecisionManagerImpl();
    // }
    //
    // @Bean
    // public SessionRegistry sessionRegistry() {
    //     return new SessionRegistryImpl();
    // }
    //
    // @Bean
    // public HttpSessionEventPublisher httpSessionEventPublisher() {
    //     return new HttpSessionEventPublisher();
    // }
    //
    // /**
    //  * 密码加密
    //  *
    //  * @return {@link PasswordEncoder} 加密方式
    //  */
    // @Bean
    // public PasswordEncoder passwordEncoder() {
    //     return new BCryptPasswordEncoder();
    // }
    //
    // /**
    //  * 配置权限
    //  *
    //  * @param http http
    //  * @throws Exception 异常
    //  */
    // @Override
    // protected void configure(HttpSecurity http) throws Exception {
    //     // 配置登录注销路径
    //     http.formLogin()
    //             .loginProcessingUrl("/login")
    //             .successHandler(authenticationSuccessHandler)
    //             .failureHandler(authenticationFailHandler)
    //             .and()
    //             .logout()
    //             .logoutUrl("/logout")
    //             .logoutSuccessHandler(logoutSuccessHandler);
    //     // 配置路由权限信息
    //     http.authorizeRequests()
    //             .withObjectPostProcessor(new ObjectPostProcessor<FilterSecurityInterceptor>() {
    //                 @Override
    //                 public <O extends FilterSecurityInterceptor> O postProcess(O fsi) {
    //                     fsi.setSecurityMetadataSource(securityMetadataSource());
    //                     fsi.setAccessDecisionManager(accessDecisionManager());
    //                     return fsi;
    //                 }
    //             })
    //             .anyRequest().permitAll()
    //             .and()
    //             // 关闭跨站请求防护
    //             .csrf().disable().exceptionHandling()
    //             // 未登录处理
    //             .authenticationEntryPoint(authenticationEntryPoint)
    //             // 权限不足处理
    //             .accessDeniedHandler(accessDeniedHandler)
    //             .and()
    //             .sessionManagement()
    //             .maximumSessions(20)
    //             .sessionRegistry(sessionRegistry());
    // }

    //配置异常处理器，自定义失败处理
    //用户未登录处理
    @Autowired
    private AuthenticationEntryPointImpl authenticationEntryPoint;
    //用户权限处理
    @Autowired
    private AccessDeniedHandlerImpl accessDeniedHandler;

    //配置认证成功、失败处理器
    //认证失败处理器，登录失败处理器
    @Autowired
    private AuthenticationFailHandlerImpl authenticationFailHandler;
    //认证成功处理器，登录成功处理器
    @Autowired
    private AuthenticationSuccessHandlerImpl authenticationSuccessHandler;

    //登出成功处理器
    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    /**
     * 接口拦截规则
     *
     * @return
     */
    @Bean
    public FilterInvocationSecurityMetadataSource securityMetadataSource() {
        return new FilterInvocationSecurityMetadataSourceImpl();
    }

    /**
     * 访问决策管理器
     *
     * @return 访问决策管理器实现类AccessDecisionManagerImpl
     */
    @Bean
    public AccessDecisionManager accessDecisionManager() {
        return new AccessDecisionManagerImpl();
    }

    /**
     * 会话注册
     *
     * @return
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * 密码加密存储：SpringSecurity自带的BCryptPasswordEncoder加密方式
     * <p>
     * 把BCryptPasswordEncoder对象注入Spring容器中，
     * SpringSecurity就会使用PasswordEncoder来进行密码校验
     *
     * @return {@link PasswordEncoder} 加密方式
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置权限
     * 重写configure方法
     *
     * @param http http
     * @throws Exception 异常
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // 重写登录表单方法
        http.formLogin()
            //todo SpringSecurity自动配置登录、注销接口路径，不用自己写
            .loginProcessingUrl("/login")
            //配置认证成功处理器，登录成功返回用户信息，都封装在authenticationSuccessHandler中
            .successHandler(authenticationSuccessHandler)
            // 配置认证失败处理器，登录失败返回失败异常信息，都封装在authenticationFailHandler
            .failureHandler(authenticationFailHandler)
            .and()
            .logout()
            .logoutUrl("/logout")
            //注销成功处理
            .logoutSuccessHandler(logoutSuccessHandler);
        // 配置路由权限信息，授权请求
        http.authorizeRequests()
            .withObjectPostProcessor(new ObjectPostProcessor<FilterSecurityInterceptor>() {
                @Override
                public <O extends FilterSecurityInterceptor> O postProcess(O fsi) {
                    fsi.setSecurityMetadataSource(securityMetadataSource());
                    fsi.setAccessDecisionManager(accessDecisionManager());
                    return fsi;
                }
            })
            // 除上面外的所有请求需要鉴权，其他请求全部允许
            //anonymous() 允许匿名用户访问、
            // authenticated() 允许认证的用户进行访问
            //hasAnyAuthority(String…)如果用户具备给定权限中的某一个的话，就允许访问
            //permitAll() 无条件允许访问
            .anyRequest().permitAll()
            .and()
            // 关闭csrf跨站请求防护，本质是跟token一样，前后端分离的项目不怕csrf攻击所以关闭
            .csrf().disable()
            //配置异常处理器：认证+授权
            .exceptionHandling()
            // 未登录处理，配置认证异常处理器
            .authenticationEntryPoint(authenticationEntryPoint)
            // 权限不足处理，配置授权异常处理器
            .accessDeniedHandler(accessDeniedHandler)
            .and()
            .sessionManagement()
            .maximumSessions(20)
            .sessionRegistry(sessionRegistry());
        //支持跨域
        http.cors();

    }

}