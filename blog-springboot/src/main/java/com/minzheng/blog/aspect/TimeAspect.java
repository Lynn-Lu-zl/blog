package com.minzheng.blog.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 时间记录切面
 * 收集接口运行时间
 */
@Aspect
@Component
public class TimeAspect {
    //切入点
    @Pointcut("@annotation(com.minzheng.blog.annotation.Timer)")
    private void pointcut(){}

    //环绕通知
    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint)throws Throwable{
        // 获取目标Logger
        Logger logger = LoggerFactory.getLogger(joinPoint.getTarget().getClass());

        // 获取目标类名称
        String clazzName = joinPoint.getTarget().getClass().getName();

        // 获取目标类方法名称
        String methodName = joinPoint.getSignature().getName();

        long start = System.currentTimeMillis();
        logger.info( "{}: {}: 开始执行...", clazzName, methodName);

        // 调用目标方法
        Object result = joinPoint.proceed();

        long time = System.currentTimeMillis() - start;
        logger.info( "{}: {}: : 结束... 执行时间: {} ms", clazzName, methodName, time);
        return result;
    }

}
