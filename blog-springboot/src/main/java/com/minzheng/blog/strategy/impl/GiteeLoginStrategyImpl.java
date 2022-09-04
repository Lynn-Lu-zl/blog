package com.minzheng.blog.strategy.impl;

import com.alibaba.fastjson.JSON;
import com.minzheng.blog.config.GiteeConfigProperties;
import com.minzheng.blog.dto.GiteeTokenDTO;
import com.minzheng.blog.dto.GiteeUserInfoDTO;
import com.minzheng.blog.dto.SocialTokenDTO;
import com.minzheng.blog.dto.SocialUserInfoDTO;
import com.minzheng.blog.enums.LoginTypeEnum;
import com.minzheng.blog.exception.BizException;
import com.minzheng.blog.vo.GiteeLoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static com.minzheng.blog.constant.SocialLoginConst.*;
import static com.minzheng.blog.enums.StatusCodeEnum.GITEE_LOGIN_ERROR;

/**
 * 码云登录策略实现
 *

 */
@Service("giteeLoginStrategyImpl")
public class GiteeLoginStrategyImpl extends AbstractSocialLoginStrategyImpl{

    @Autowired
    private GiteeConfigProperties giteeConfigProperties;

    @Autowired
    private RestTemplate restTemplate;

    private GiteeTokenDTO getGiteeToken(GiteeLoginVO giteeLoginVO) {
        // 根据code换取微博uid和accessToken
        //LinkedMultiValueMap链接多值映射
        MultiValueMap<String, String> giteeData = new LinkedMultiValueMap<>();
        // 定义微博token请求参数
        giteeData.add(CLIENT_ID, giteeConfigProperties.getClientId());
        giteeData.add(CLIENT_SECRET, giteeConfigProperties.getClientSecret());
        giteeData.add(GRANT_TYPE, giteeConfigProperties.getGrantType());
        giteeData.add(REDIRECT_URI, giteeConfigProperties.getRedirectUrl());
        giteeData.add(CODE, giteeLoginVO.getCode());
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(giteeData, null);
        try {
            return restTemplate.exchange(giteeConfigProperties.getAccessTokenUrl(), HttpMethod.POST, requestEntity, GiteeTokenDTO.class).getBody();
        } catch (Exception e) {
            throw new BizException(GITEE_LOGIN_ERROR);
        }
    }

    /**
     * 获取第三方token信息
     *
     * @param data 数据
     * @return {@link SocialTokenDTO} 第三方token信息
     */
    @Override
    public SocialTokenDTO getSocialToken(String data) {
        //accessToken属性
        GiteeLoginVO giteeLoginVO = JSON.parseObject(data, GiteeLoginVO.class);
        // 获取Gitee token信息
        GiteeTokenDTO giteeToken = getGiteeToken(giteeLoginVO);
        //返回token信息
        return SocialTokenDTO.builder()
            //.openId(CommonConst.DEFAULT_NICKNAME + IdWorker.getId())
            .accessToken(giteeToken.getAccess_token())
            .loginType(LoginTypeEnum.GITEE.getType())
            .build();
    }

    /**
     * 获取第三方用户信息
     *
     * @param socialTokenDTO 第三方token信息
     * @return {@link SocialUserInfoDTO} 第三方用户信息
     */
    @Override
    public SocialUserInfoDTO getSocialUserInfo(SocialTokenDTO socialTokenDTO) {
        // 定义请求参数
        Map<String, String> data = new HashMap<>(2);
        data.put(ACCESS_TOKEN,socialTokenDTO.getAccessToken());
        //获取码云用户信息
        GiteeUserInfoDTO giteeUserInfoDTO = restTemplate.getForObject(giteeConfigProperties.getUserInfoUrl(), GiteeUserInfoDTO.class, data);

        return SocialUserInfoDTO.builder()
            .nickname(giteeUserInfoDTO.getName())
            .avatar(giteeUserInfoDTO.getAvatar_url())
            .build();
    }
}
