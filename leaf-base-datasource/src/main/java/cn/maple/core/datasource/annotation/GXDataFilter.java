/**
 * Copyright (c) 2018 人人开源 All rights reserved.
 * <p>
 * https://www.renren.io
 * <p>
 * 版权所有，侵权必究！
 */

package cn.maple.core.datasource.annotation;

import java.lang.annotation.*;

/**
 * 数据过滤注解
 * <p>
 * 该注解用于在方法级别实现数据权限过滤，通常应用在Service层或Mapper层的查询方法上。
 * 当标记了此注解的方法被调用时，系统会自动根据当前用户的权限范围过滤数据，
 * 确保用户只能访问其权限范围内的数据。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 在Service方法上使用
 * @GXDataFilter(tableAlias = "t1", userIdFieldNames = {"creator_id", "user_id"})
 * public List<UserEntity> getUserList(Map<String, Object> params) {
 *     return baseDao.selectByMap(params);
 * }
 * 
 * // 在自定义Mapper方法上使用
 * @GXDataFilter(tableAlias = "t", deptIdFieldNames = {"department_id"})
 * List<OrderEntity> getOrdersByDept(Map<String, Object> params);
 * </pre>
 * 
 * <p>工作原理：</p>
 * <p>1. 通过AOP拦截标记了@GXDataFilter注解的方法</p>
 * <p>2. 根据当前用户的权限信息构建SQL过滤条件</p>
 * <p>3. 通过MyBatis拦截器将过滤条件动态添加到SQL语句中</p>
 * 
 * <p>注意事项：</p>
 * <p>1. 确保表中包含用户ID或部门ID字段</p>
 * <p>2. 如果表使用了别名，需要在tableAlias属性中指定</p>
 * <p>3. 可以同时过滤用户ID和部门ID</p>
 * 
 * @author 塵渊 britton@126.com
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXDataFilter {
    /**
     * 表的别名
     * <p>
     * 如果SQL查询语句中使用了表别名，需要在此指定，以便正确构建过滤条件
     * 例如：SELECT * FROM sys_user t WHERE t.id = ?，则tableAlias="t"
     * </p>
     */
    String tableAlias() default "";

    /**
     * 用户ID字段名数组
     * <p>
     * 指定表中哪些字段存储了用户ID，用于按用户权限过滤数据
     * 可以指定多个字段，系统会自动构建OR条件
     * </p>
     */
    String[] userIdFieldNames() default {"user_id"};

    /**
     * 部门ID字段名数组
     * <p>
     * 指定表中哪些字段存储了部门ID，用于按部门权限过滤数据
     * 可以指定多个字段，系统会自动构建OR条件
     * </p>
     */
    String[] deptIdFieldNames() default {"dept_id"};
}