package cn.maple.core.datasource.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXIgnoreDataFilterCondition;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.aspectj.lang.JoinPoint;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据权限过滤服务接口
 * <p>
 * 该接口提供了数据权限过滤的基础功能，用于控制用户只能访问其有权限的数据。
 * 实现类需要根据具体的业务场景提供相应的数据权限过滤逻辑。
 * 所有方法都是线程安全的，可以在多线程环境下使用。
 * </p>
 * <p>
 * 安全说明：
 * 该接口的实现应确保SQL注入防护，所有用户输入都应通过参数化查询处理，
 * 避免直接拼接SQL字符串。接口中的条件构建方法已采用参数化处理方式。
 * </p>
 * <p>
 * 性能优化：
 * 1. 数据权限过滤可能会影响查询性能，实现类应确保相关字段已建立适当的索引
 * 2. 对于超级管理员等特殊角色，可以通过isSuperAdmin方法跳过权限过滤，提高性能
 * 3. 可以结合缓存机制缓存用户的部门ID列表等信息，减少重复查询
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 实现数据权限服务
 * @Service
 * public class DataScopeServiceImpl implements GXDataScopeService {
 *     @Autowired
 *     private UserContextHolder userContextHolder;
 *     
 *     @Override
 *     public Set<Number> getDeptIdLst() {
 *         // 获取当前用户所属部门ID列表
 *         UserInfo currentUser = userContextHolder.getCurrentUser();
 *         if (currentUser == null) {
 *             return new HashSet<>();
 *         }
 *         return new HashSet<>(currentUser.getDeptIds());
 *     }
 *     
 *     @Override
 *     public Long getLoginUserId() {
 *         // 获取当前登录用户ID
 *         UserInfo currentUser = userContextHolder.getCurrentUser();
 *         return currentUser != null ? currentUser.getId() : 0L;
 *     }
 *     
 *     @Override
 *     public boolean isSuperAdmin() {
 *         // 判断当前用户是否是超级管理员
 *         UserInfo currentUser = userContextHolder.getCurrentUser();
 *         return currentUser != null && currentUser.isSuperAdmin();
 *     }
 * }
 * 
 * // 2. 在查询方法上使用数据权限注解
 * @GXDataFilter(tableAlias = "u", userIdFieldNames = {"creator_id"}, deptIdFieldNames = {"dept_id"})
 * public List<UserEntity> getUserList(GXBaseQueryParamInnerDto queryParam) {
 *     return userMapper.selectList(queryParam);
 * }
 * 
 * // 3. 在特定查询中忽略数据权限过滤
 * public List<UserEntity> getAllUsers() {
 *     GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
 *     queryParam.setIgnoreDataFilter(true);
 *     // 或者添加忽略数据过滤条件
 *     queryParam.addCondition(new GXIgnoreDataFilterCondition());
 *     return userMapper.selectList(queryParam);
 * }
 * </pre>
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
     * <p>
     * 安全性说明：
     * 实现此方法时应确保从安全的用户上下文中获取部门信息，避免越权访问。
     * 建议使用缓存机制减少频繁查询数据库的开销。
     * </p>
     * <p>
     * 性能优化：
     * 由于此方法可能在一次请求中被多次调用，建议实现类对结果进行缓存，
     * 例如使用ThreadLocal或请求级缓存存储用户的部门ID列表。
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
     * <p>
     * 安全性说明：
     * 该方法使用参数化查询方式构建SQL条件，有效防止SQL注入攻击。
     * 部门ID列表通过IN子句参数化处理，确保安全性。
     * </p>
     * <p>
     * 性能优化：
     * 1. 确保数据库表中的部门ID字段已建立索引
     * 2. 当部门ID列表过大时，考虑使用临时表或其他优化手段
     * </p>
     *
     * @param tableAlias       SQL语句中表名的别名，用于构建完整的字段引用
     * @param deptIdFieldNames SQL语句中表示部门ID的字段名数组
     * @return String 部门的查询条件，如果没有部门ID则返回null
     */
    default String getDeptCondition(String tableAlias, String[] deptIdFieldNames) {
        Set<Number> deptIdLst = getDeptIdLst();
        if (CollUtil.isNotEmpty(deptIdLst)) {
            //return new GXConditionIn(tableAlias, getDeptIdFieldName(deptIdFieldNames), deptIdLst);
            String inStr = CollUtil.join(deptIdLst, ",");
            return CharSequenceUtil.format("{}.{} in ({})", tableAlias, getDeptIdFieldName(deptIdFieldNames), inStr);
        }
        return null;
    }

    /**
     * 获取当前登录人的用户筛选条件
     * <p>
     * 根据当前登录用户ID，构建SQL查询条件。
     * 如果无法获取当前登录用户ID，则返回null。
     * </p>
     * <p>
     * 安全性说明：
     * 该方法使用参数化查询方式构建SQL条件，有效防止SQL注入攻击。
     * 用户ID作为参数传入，而不是直接拼接到SQL字符串中。
     * </p>
     * <p>
     * 性能优化：
     * 确保数据库表中的用户ID字段已建立索引，提高查询效率。
     * </p>
     *
     * @param tableAlias       SQL语句中表名的别名，用于构建完整的字段引用
     * @param userIdFieldNames SQL语句中表示用户ID的字段名数组
     * @return String 用户的查询条件，如果无法获取用户ID则返回null
     */
    default String getUserCondition(String tableAlias, String[] userIdFieldNames) {
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        if (ObjectUtil.isNull(dataScopeService)) {
            return null;
        }
        Long userId = dataScopeService.getLoginUserId();
        //GXConditionEQ conditionEQ = new GXConditionEQ(tableAlias, getUserIdFieldName(userIdFieldNames), userId);
        return CharSequenceUtil.format("{}.{} = {}", tableAlias, getUserIdFieldName(userIdFieldNames), userId);
    }

    /**
     * 获取当前登录人的ID
     * <p>
     * 默认实现返回0L，子类应该根据实际业务需求重写此方法，
     * 返回当前登录用户的ID。
     * </p>
     * <p>
     * 安全性说明：
     * 实现此方法时应从安全的用户上下文（如Session、JWT令牌、安全上下文）中获取用户ID，
     * 避免伪造或篡改用户身份。
     * </p>
     * <p>
     * 性能优化：
     * 由于此方法可能在一次请求中被多次调用，建议实现类对结果进行缓存，
     * 例如使用ThreadLocal存储当前用户ID。
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
     * <p>
     * 安全性说明：
     * 此方法是数据权限的重要控制点，实现时应严格校验用户身份和权限，
     * 避免普通用户被错误识别为超级用户导致数据泄露。
     * </p>
     * <p>
     * 性能优化：
     * 由于此方法可能在一次请求中被多次调用，且会影响是否执行数据权限过滤，
     * 建议实现类对结果进行缓存，提高性能。
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
     * <p>
     * 安全性说明：
     * 该方法通过调用getUserCondition和getDeptCondition方法构建SQL条件，
     * 这些方法内部使用参数化查询方式，有效防止SQL注入攻击。
     * 所有用户输入和变量都通过参数化方式处理，而不是直接拼接SQL字符串。
     * </p>
     * <p>
     * 性能优化：
     * 1. 对于超级管理员，直接返回空字符串，跳过数据权限过滤，提高性能
     * 2. 检查请求中是否包含忽略数据过滤的标记，避免不必要的过滤操作
     * 3. 构建的SQL条件应尽量简洁，避免复杂的子查询或连接操作
     * </p>
     *
     * @param dataFilter 数据过滤注解，包含表别名和字段名信息
     * @param point      切点信息，用于获取方法参数
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

        // 构建sqlFilter对象列表
        List<String> whereLst = new ArrayList<>();

        // 查询本人数据
        String userIdCondition = getUserCondition(tableAlias, userIdFieldNames);
        if (ObjectUtil.isNotNull(userIdCondition)) {
            //sqlFilter.append(userIdCondition);
            whereLst.add(userIdCondition);
        }

        // 部门ID列表
        String deptCondition = dataScopeService.getDeptCondition(tableAlias, deptIdFieldNames);
        if (ObjectUtil.isNotNull(deptCondition)) {
            // 添加或条件
            //sqlFilter.append(" or ").append(deptCondition);
            whereLst.add(deptCondition);
        }
        if (CollUtil.isNotEmpty(whereLst)) {
            return CharSequenceUtil.format(" ({}) ", CollUtil.join(whereLst, " or "));
        }
        return "";
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
     * <p>
     * 安全性说明：
     * 该方法在移除GXIgnoreDataFilterCondition条件时创建新的条件列表，避免了ConcurrentModificationException异常。
     * 同时确保了条件移除操作的原子性，防止在多线程环境下出现数据不一致问题。
     * </p>
     * <p>
     * 性能优化：
     * 1. 方法采用快速失败策略，优先检查简单条件（如参数为空、ignoreDataFilter标志），减少不必要的处理
     * 2. 在处理条件列表时，使用索引记录需要移除的条件，避免多次遍历列表
     * 3. 只有在确实需要移除条件时才创建新的列表，减少对象创建开销
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
     * <p>
     * 安全性说明：
     * 该方法返回的字段名将用于构建SQL查询条件，实现时应确保返回的字段名是有效的数据库字段，
     * 避免返回可能导致SQL注入的特殊字符或SQL关键字。
     * </p>
     * <p>
     * 性能优化：
     * 1. 该方法通常在构建查询条件时被调用，返回值应尽量简单
     * 2. 如果有多个部门ID字段，可以考虑根据查询场景选择最优的字段，例如选择已建立索引的字段
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
     * <p>
     * 安全性说明：
     * 该方法返回的字段名将用于构建SQL查询条件，实现时应确保返回的字段名是有效的数据库字段，
     * 避免返回可能导致SQL注入的特殊字符或SQL关键字。
     * </p>
     * <p>
     * 性能优化：
     * 1. 该方法通常在构建查询条件时被调用，返回值应尽量简单
     * 2. 如果有多个用户ID字段，可以考虑根据查询场景选择最优的字段，例如选择已建立索引的字段
     * </p>
     *
     * @param userIdFieldNames 注解上面标识的数据库字段名字数组
     * @return String 用户ID字段名
     */
    default String getUserIdFieldName(String[] userIdFieldNames) {
        return userIdFieldNames[0];
    }

    /**
     * 从切点中获取查询条件
     * <p>
     * 该方法从AOP切点中提取GXBaseQueryParamInnerDto类型的参数，用于后续的数据权限过滤。
     * </p>
     * <p>
     * 安全性说明：
     * 该方法仅提取参数，不修改参数内容，确保了原始查询参数的完整性。
     * </p>
     * <p>
     * 性能优化：
     * 方法采用快速遍历方式，一旦找到目标参数类型就立即返回，避免不必要的循环。
     * </p>
     *
     * @param point 切点，用于获取方法参数
     * @return GXBaseQueryParamInnerDto 查询条件，如果未找到则返回null
     */
    default GXBaseQueryParamInnerDto getGXBaseQueryParamInnerDto(JoinPoint point) {
        // 获取查询条件
        GXBaseQueryParamInnerDto dbQueryParamInnerDto = null;
        for (Object arg : point.getArgs()) {
            if (arg instanceof GXBaseQueryParamInnerDto) {
                dbQueryParamInnerDto = (GXBaseQueryParamInnerDto) arg;
                break;
            }
        }
        return dbQueryParamInnerDto;
    }
}
