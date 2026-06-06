package com.teamflow.ai.common.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法上，触发操作日志异步记录。
 * module：所属模块（如 "用户管理"）
 * type：操作类型（如 "新增"/"修改"/"删除"）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    String module() default "";

    String type() default "";
}
