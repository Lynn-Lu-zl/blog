package com.minzheng.blog.handler;

import com.alibaba.fastjson.JSON;

import com.minzheng.blog.vo.Result;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.minzheng.blog.constant.CommonConst.APPLICATION_JSON;

/**
 * 用户权限处理
 * 实现security自带的拒绝访问处理程序接口AccessDeniedHandler
 * 自定义失败处理
 *  如果是授权过程中出现的异常会被封装成AccessDeniedException
 *  然后调用AccessDeniedHandler对象的方法去进行异常处理。
 *

 */
@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AccessDeniedException e) throws IOException {
        httpServletResponse.setContentType(APPLICATION_JSON);
        httpServletResponse.getWriter().write(JSON.toJSONString(Result.fail("权限不足")));
    }

}
