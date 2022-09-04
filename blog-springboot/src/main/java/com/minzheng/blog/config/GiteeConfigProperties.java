package com.minzheng.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 码云配置属性
 *
 **/
@Data
@Configuration
@ConfigurationProperties(prefix = "gitee")
public class GiteeConfigProperties {

    /**
     * 客户端Id
     */
    private String clientId;

    /**
     * 客户端Secret
     */
    private String clientSecret;

    /**
     * 登录类型
     */
    private String grantType;

    /**
     * 回调域名
     */
    private String redirectUrl;

    /**
     * 访问令牌地址
     */
    private String accessTokenUrl;

    /**
     * 用户信息地址
     */
    private String userInfoUrl;

}
