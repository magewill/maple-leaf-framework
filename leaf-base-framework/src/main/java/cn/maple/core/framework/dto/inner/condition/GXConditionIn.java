package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * IN条件查询构建类（数值类型）
 * <p>
 * 该类用于构建SQL的IN条件查询，专门处理数值类型（Number）的集合。
 * 支持参数化查询，自动生成参数占位符，并提供安全的参数绑定机制。
 * 内置查询条件数量限制，防止过大的IN查询导致性能问题。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 创建数值类型的IN条件
 * Set<Number> idSet = new HashSet<>();
 * idSet.add(1);
 * idSet.add(2);
 * idSet.add(3);
 * GXConditionIn condition = new GXConditionIn("t", "user_id", idSet);
 * 
 * // 2. 在查询参数中使用
 * GXBaseQueryParamInnerDto queryParam = new GXBaseQueryParamInnerDto();
 * queryParam.addCondition(condition);
 * 
 * // 3. 在Mapper方法中使用
 * List<UserEntity> users = userMapper.selectByCondition(queryParam);
 * </pre>
 * 
 * <p>生成的SQL示例：</p>
 * <pre>
 * -- 假设参数为：tableNameAlias="t", fieldName="user_id", numbers={1,2,3}
 * -- 生成的SQL片段为：
 * t.user_id in (#{dbQueryParamInnerDto.paramMap.COND_0_0}, #{dbQueryParamInnerDto.paramMap.COND_0_1}, #{dbQueryParamInnerDto.paramMap.COND_0_2})
 * </pre>
 * 
 * <p>安全特性：</p>
 * <ol>
 *   <li>参数化查询：避免SQL注入风险</li>
 *   <li>数量限制：防止过大的IN查询导致数据库性能问题</li>
 *   <li>环境感知：在开发环境和生产环境使用不同的数量限制</li>
 * </ol>
 * 
 * @author 塵渊 britton@126.com
 */
public class GXConditionIn extends GXCondition<String> {
    /**
     * 数值集合，用于IN条件查询
     * 存储需要进行IN条件查询的所有数值
     */
    private final Set<Number> numbers;

    /**
     * 构造函数
     * <p>
     * 创建一个数值类型的IN条件查询对象
     * </p>
     *
     * @param tableNameAlias 表别名，可以为空
     * @param fieldName      字段名称
     * @param value          数值集合，包含所有需要进行IN查询的值
     */
    public GXConditionIn(String tableNameAlias, String fieldName, Set<Number> value) {
        super(tableNameAlias, fieldName, value);
        this.numbers = value;
    }

    /**
     * 获取操作符
     * <p>
     * 返回SQL操作符"in"，用于构建IN条件查询
     * </p>
     *
     * @return 返回字符串"in"
     */
    @Override
    public String getOp() {
        return "in";
    }

    /**
     * 生成WHERE子句字符串
     * <p>
     * 根据表别名、字段名和数值集合，生成参数化的IN条件SQL片段
     * 该方法会检查IN条件的数量限制，防止过大的IN查询导致性能问题
     * 在开发环境和生产环境使用不同的数量限制
     * </p>
     *
     * @return 返回格式化的WHERE子句字符串，例如："t.user_id in (#{param1}, #{param2}, #{param3})"
     * @throws GXBusinessException 当IN条件数量超过限制时抛出异常
     */
    @Override
    public String whereString() {
        // 获取当前运行环境
        String activeProfile = GXCommonUtils.getActiveProfile();
        // 默认限制数量（生产环境）
        int limitCnt = 100000;
        // 开发环境列表
        List<String> envLst = CollUtil.newArrayList(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        // 如果是开发环境，使用配置的限制数量，默认为50
        if (CollUtil.contains(envLst, activeProfile)) {
            limitCnt = GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, 50);
        }
        // 检查IN条件数量是否超过限制
        if (CollUtil.size(numbers) > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("IN查询条件不能超过{}条数据!", limitCnt));
        }

        // 构建参数化的IN子句
        List<String> paramPlaceholders = new ArrayList<>();
        int index = 0;
        for (Number ignored : numbers) {
            // 为每个值创建唯一的参数名
            String itemParamName = paramName + "_" + index++;
            // 添加MyBatis参数占位符
            paramPlaceholders.add("#{dbQueryParamInnerDto.paramMap." + itemParamName + "}");
        }

        // 将所有参数占位符用逗号连接
        String inClause = String.join(",", paramPlaceholders);

        // 根据是否有表别名构建不同格式的SQL
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} ({})", getFieldExpression(), getOp(), inClause);
        }
        return CharSequenceUtil.format("{}.{} {} ({})", tableNameAlias, getFieldExpression(), getOp(), inClause);
    }

    /**
     * 获取字段值并设置参数映射
     * <p>
     * 该方法为IN条件的每个值创建单独的参数映射
     * 由于IN条件的特殊性，需要为集合中的每个元素创建单独的参数
     * </p>
     *
     * @return 返回空字符串，实际值已通过参数映射处理
     */
    @Override
    public String getFieldValue() {
        // 清除原来的参数映射，因为IN条件需要特殊处理
        this.paramMap.clear();
        // 为每个值创建单独的参数
        int index = 0;
        for (Number num : numbers) {
            String itemParamName = paramName + "_" + index++;
            this.paramMap.put(itemParamName, num);
        }
        // 此方法不再使用，但为了兼容性保留
        return "";
    }
}
