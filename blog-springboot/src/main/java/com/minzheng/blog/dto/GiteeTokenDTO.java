package com.minzheng.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 码云token
 *
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GiteeTokenDTO {

    // /**
    //  * uid
    //  */
    // private String uid;

    /**
     * 访问令牌
     */
    private String access_token;

}
