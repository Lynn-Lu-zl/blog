package com.minzheng.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * gitee用户信息dto
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GiteeUserInfoDTO {

    /**
     * 昵称
     */
    private String name;

    /**
     * qq头像
     */
    private String avatar_url;


}
