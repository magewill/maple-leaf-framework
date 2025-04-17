package cn.maple.core.datasource.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionIn;
import cn.maple.core.framework.dto.inner.condition.GXIgnoreDataFilterCondition;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.aspectj.lang.JoinPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限过滤服务接口
 * <p>
 * 该接口提供了数据权限过滤的基础功能，用于控制用户只能访问其有权限的数据。
 * 实现类需要根据具体的业务场景提供相应的数据权限过滤逻辑。
 * 所有方法都是线程安全的，可以在多线程环境下使用。
 * </p>
 *
 * @author 塵渊 britton@126.com
 */
public interface GXDataScopeService {
    /**
     * 获取当前登录用户所属的部门ID列表
     * <p>
     * 默认实现返回空集合，子类应该根据实际业务需求重写此方法，
     * 返回当前登录用户所属的所有部门ID。
     * </p>
     *
     * @return Set<Number> 部门ID集合，如果用户没有所属部门则返回空集合
     */
    default Set<Number> getDeptIdLst() {
        return new HashSet<>();
    }

    /**
     * 获取当前登录人的部门筛选条件
     * <p>
     * 根据当前登录用户所属的部门ID列表，构建SQL查询条件。
     * 如果部门ID列表为空，则返回null。
     * </p>
     *
     * @param tableAlias       SQL语句中表名的别名，用于构建完整的字段引用
     * @param deptIdFieldNames SQL语句中表示部门ID的字段名数组
     * @return GXCondition<?> 部门的查询条件，如果没有部门ID则返回null
     */
    default GXCondition<?> getDeptCondition(String tableAlias, String[] deptIdFieldNames) {
        Set<Number> deptIdLst = getDeptIdLst();
        if (CollUtil.isNotEmpty(deptIdLst)) {
            return new GXConditionIn(tableAlias, getDeptIdFieldName(deptIdFieldNames), deptIdLst);
        }
        return null;
    }

    /**
     * 获取当前登录人的用户筛选条件
     * <p>
     * 根据当前登录用户ID，构建SQL查询条件。
     * 如果无法获取当前登录用户ID，则返回null。
     * </p>
     *
     * @param tableAlias       SQL语句中表名的别名，用于构建完整的字段引用
     * @param userIdFieldNames SQL语句中表示用户ID的字段名数组
     * @return GXCondition<?> 用户的查询条件，如果无法获取用户ID则返回null
     */
    default GXCondition<?> getUserCondition(String tableAlias, String[] userIdFieldNames) {
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        if (ObjectUtil.isNull(dataScopeService)) {
            return null;
        }
        Long userId = dataScopeService.getLoginUserId();
        return new GXConditionEQ(tableAlias, getUserIdFieldName(userIdFieldNames), userId);
    }

    /**
     * 获取当前登录人的ID
     * <p>
     * 默认实现返回0L，子类应该根据实际业务需求重写此方法，
     * 返回当前登录用户的ID。
     * </p>
     *
     * @return Long 当前登录用户ID，默认为0L
     */
    default Long getLoginUserId() {
        return 0L;
    }

    /**
     * 判断当前登录人是否是超级用户
     * <p>
     * 超级用户通常不需要数据权限过滤，可以访问所有数据。
     * 默认实现返回false，子类应该根据实际业务需求重写此方法。
     * </p>
     *
     * @return boolean 如果是超级用户返回true，否则返回false
     */
    default boolean isSuperAdmin() {
        return false;
    }

    /**
     * 组合SQL Filter语句
     * <p>
     * 根据数据过滤注解和切点信息，构建SQL过滤条件。
     * 如果当前用户是超级管理员或者请求中包含忽略数据过滤的标记，则不进行数据过滤。
     * </p>
     *
     * @param dataFilter 数据过滤注解，包含表别名和字段名信息
     * @param point 切点信息，用于获取方法参数
     * @return String SQL过滤语句，如果不需要过滤则返回空字符串
     */
    default String getSqlFilter(GXDataFilter dataFilter, JoinPoint point) {
        boolean hasIgnoreDataFilterCondition = checkIgnoreDataFilter(point);
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        if (hasIgnoreDataFilterCondition || ObjectUtil.isNull(dataScopeService)) {
            return "";
        }
        // 获取表的别名
        String tableAlias = dataFilter.tableAlias();

        // 表字段的名字
        String[] deptIdFieldNames = dataFilter.deptIdFieldNames();
        String[] userIdFieldNames = dataFilter.userIdFieldNames();

        // 构建sqlFilter对象
        StringBuilder sqlFilter = new StringBuilder();
        sqlFilter.append(" (");

        // 查询本人数据
        GXCondition<?> userIdCondition = getUserCondition(tableAlias, userIdFieldNames);
        sqlFilter.append(userIdCondition.whereString());

        // 部门ID列表
        GXCondition<?> deptCondition = dataScopeService.getDeptCondition(tableAlias, deptIdFieldNames);
        if (ObjectUtil.isNotNull(deptCondition)) {
            String s = deptCondition.whereString();
            // 添加或条件
            sqlFilter.append(" or ").append(s);
        }
        sqlFilter.append(")");
        return sqlFilter.toString();
    }

    /**
     * 检查是否需要忽略数据权限过滤
     * <p>
     * 处理参数中是否带有GXIgnoreDataFilterCondition条件，如果带有该条件，则不需要数据权限过滤，
     * 并且需要将其从查询条件移除。如果加上了该条件，则不会有数据权限过滤的功能。
     * </p>
     * <p>
     * 该方法会检查以下情况：
     * 1. 如果方法参数为空，则忽略数据过滤
     * 2. 如果方法参数中没有GXBaseQueryParamInnerDto类型的参数，则忽略数据过滤
     * 3. 如果GXBaseQueryParamInnerDto的ignoreDataFilter属性为true，则忽略数据过滤
     * 4. 如果GXBaseQueryParamInnerDto的条件列表中包含GXIgnoreDataFilterCondition类型的条件，则忽略数据过滤
     * </p>
     *
     * @param point 切点，用于获取方法参数
     * @return boolean 是否有不需要数据过滤的条件，true表示需要忽略数据过滤
     */
    private boolean checkIgnoreDataFilter(JoinPoint point) {
        List<Object> args = Arrays.asList(point.getArgs());
        if (CollUtil.isEmpty(List.of(args))) {
            return true;
        }
        List<Object> argsLst = args.stream().filter(t -> t.getClass().isAssignableFrom(GXBaseQueryParamInnerDto.class)).collect(Collectors.toList());
        if (CollUtil.isEmpty(argsLst)) {
            return true;
        }
        GXBaseQueryParamInnerDto queryParams = (GXBaseQueryParamInnerDto) argsLst.get(0);
        // 如果手动设置了true 就直接返回
        if (queryParams.isIgnoreDataFilter()) {
            return true;
        }
        // 处理条件中含有GXIgnoreDataFilterCondition类的条件
        List<GXCondition<?>> conditionLst = queryParams.getCondition();
        if (CollUtil.isNotEmpty(conditionLst)) {
            // 创建一个新的列表，避免ConcurrentModificationException
            List<GXCondition<?>> newConditionList = new ArrayList<>();
            List<Integer> removeIndexLst = CollUtil.newArrayList();
            
            for (int i = 0, len = conditionLst.size(); i < len; i++) {
                if (!conditionLst.get(i).getClass().isAssignableFrom(GXIgnoreDataFilterCondition.class)) {
                    newConditionList.add(conditionLst.get(i));
                } else {
                    removeIndexLst.add(i);
                }
            }
            
            // 替换原有条件列表
            queryParams.setCondition(newConditionList);
            return CollUtil.isNotEmpty(removeIndexLst);
        }
        return false;
    }

    /**
     * 获取"标识"部门字段的数据库表的名字 eg dept_id
     * <p>
     * 从字段名数组中获取第一个字段名作为部门ID字段名。
     * 如果需要使用多个字段名，子类可以重写此方法。
     * </p>
     *
     * @param deptIdFieldNames 注解上面标识的数据库字段名字数组
     * @return String 部门ID字段名
     */
    default String getDeptIdFieldName(String[] deptIdFieldNames) {
        return deptIdFieldNames[0];
    }

    /**
     * 获取"标识"用户字段的数据库表的名字 eg user_id
     * <p>
     * 从字段名数组中获取第一个字段名作为用户ID字段名。
     * 如果需要使用多个字段名，子类可以重写此方法。
     * </p>
     *
     * @param userIdFieldNames 注解上面标识的数据库字段名字数组
     * @return String 用户ID字段名
     */
    default String getUserIdFieldName(String[] userIdFieldNames) {
        return userIdFieldNames[0];
    }
}
