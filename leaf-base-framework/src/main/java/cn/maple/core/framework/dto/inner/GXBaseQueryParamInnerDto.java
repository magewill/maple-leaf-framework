package cn.maple.core.framework.dto.inner;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.maple.core.framework.dto.GXBaseDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;

/**
 * 查询参数内部DTO基类
 * <p>
 * 该类用于封装数据库查询的各种参数，支持复杂的查询条件构建、分页、排序、分组等操作
 * 通过Builder模式提供流畅的API，便于构建复杂查询
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建基本查询参数
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("users")
 *     .tableNameAlias("u")
 *     .page(1)
 *     .pageSize(10)
 *     .build();
 * 
 * // 添加查询条件
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("status", 1));
 * conditions.add(new GXConditionGT("age", 18));
 * queryParam.setCondition(conditions);
 * 
 * // 设置排序
 * Map<String, String> orderBy = new HashMap<>();
 * orderBy.put("created_at", "desc");
 * queryParam.setOrderByField(orderBy);
 * 
 * // 设置分组
 * Set<String> groupBy = new HashSet<>();
 * groupBy.add("department_id");
 * queryParam.setGroupByField(groupBy);
 * 
 * // 添加JOIN查询
 * List<GXJoinDto> joins = new ArrayList<>();
 * GXJoinDto joinDto = GXJoinDto.builder()
 *     .joinTableName("departments")
 *     .joinTableNameAlias("d")
 *     .masterTableNameAlias("u")
 *     .joinType(GXJoinTypeEnums.LEFT)
 *     .build();
 * joins.add(joinDto);
 * queryParam.setJoins(joins);
 * </pre>
 * </p>
 * 
 * <p>
 * 安全性说明：
 * 1. 该类通过参数化查询机制(paramMap)防止SQL注入攻击
 * 2. 所有的查询条件、排序字段等都应通过该类的属性设置，避免直接拼接SQL
 * 3. 对于rawSQL属性，应谨慎使用，确保其内容安全可控
 * 4. 在处理用户输入时，应先进行验证和清洗，再设置到该类的属性中
 * </p>
 * 
 * <p>
 * 性能优化说明：
 * 1. 合理设置page和pageSize，避免一次查询过多数据
 * 2. 适当使用limit限制返回结果数量
 * 3. 对于大数据量查询，可设置paginateCount为false，避免额外的count查询
 * 4. 合理设置查询条件，避免全表扫描
 * 5. 使用参数化查询(paramMap)可提高SQL复用率，减少数据库解析负担
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
@Data
@Builder
public class GXBaseQueryParamInnerDto extends GXBaseDto {
    /**
     * 需要查询的主表名字(在有join查询时,需要有主表、次表的区分)
     */
    private String tableName;

    /**
     * 需要查询的主表名字的别名(在有join查询时,需要有主表、次表的区分)
     */
    private String tableNameAlias;

    /**
     * 当前页
     */
    private Integer page;

    /**
     * 每页数据条数
     */
    private Integer pageSize;

    /**
     * 搜索条件
     */
    @Builder.Default
    private List<GXCondition<?>> condition = new ArrayList<>();

    /**
     * 需要查询的数据列
     */
    private Set<String> columns;

    /**
     * 排序字段
     * eg:
     * <code>
     * Map&lt;String ,String&gt; orderByField = new HashMap<>();
     * orderByField.put("created_at" , "desc");
     * orderByField.put("username" , "asc");
     * </code>
     */
    private Map<String, String> orderByField;

    /**
     * 分组字段
     */
    private Set<String> groupByField;

    /**
     * GXBaseData及其子类中的方法名字
     * 通常用于在需要额外处理一些逻辑时自动调用
     */
    private String methodName;

    /**
     * SQL中的having条件
     * <pre>
     * {@code
     * Set<String> having = CollUtil.newHashSet("SUM(area)>1000000" , "SUM(price) >= 1000");
     * }
     * </pre>
     */
    private Set<String> having;

    /**
     * 对象复制时的一些额外配置
     */
    private CopyOptions copyOptions;

    /**
     * 限制条数
     */
    private Integer limit;

    /**
     * JOIN链接信息
     */
    private List<GXJoinDto> joins;

    /**
     * 额外参数
     * <p>
     * 配合methodName一起使用
     */
    private Object extraData;

    /**
     * 应用端自己提供的SQL
     * 如果提供了该字段
     * 则框架不会在自动处理其他的SQL处理拼接
     */
    private String rawSQL;

    /**
     * 忽略数据权限
     * 设置为TRUE的话 就忽略掉数据权限处理
     */
    @Builder.Default
    private boolean ignoreDataFilter = Boolean.FALSE;

    /**
     * Mybatis Plus是否使用count(*)查询
     * 如果Mybatis Plus提供的count(*)有性能问题， 可以将其设置为False, 业务自己对count(*)进行优化
     */
    @Builder.Default
    private boolean paginateCount = Boolean.TRUE;

    /**
     * MyBatis参数化参数参数映射
     */
    @Builder.Default
    private Map<String, Object> paramMap = new HashMap<>();
}
