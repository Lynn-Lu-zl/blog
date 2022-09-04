package com.minzheng.blog.annotation;

import java.lang.annotation.*;

/**
 * AOP 记录执行方法时间
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Timer {

}
