package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 字符串类型IN条件构建类
 * <p>
 * 该类用于构建SQL中的字符串类型IN条件，采用MyBatis参数化查询机制，
 * 有效防止SQL注入攻击。每个值都会被单独参数化处理，确保查询安全。
 * </p>
 * 
 * <p>
 * 安全特性：
 * <ul>
 *   <li>使用MyBatis参数化查询(#{})替代字符串拼接，彻底防止SQL注入</li>
 *   <li>对每个值进行SQL注入检查，发现可疑内容立即抛出异常</li>
 *   <li>限制IN子句中的参数数量，防止超大查询导致性能问题</li>
 *   <li>环境感知的参数限制，开发环境和生产环境使用不同的限制值</li>
 *   <li>为每个值创建独立的参数名，避免参数混淆</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建一个字符串集合作为IN条件的值
 * Set<String> roleSet = new HashSet<>();
 * roleSet.add("admin");
 * roleSet.add("manager");
 * 
 * // 2. 创建IN条件（WHERE u.role IN ('admin', 'manager')）
 * GXConditionStrIn condition = new GXConditionStrIn("u", "role", roleSet);
 * 
 * // 3. 在查询构建器中使用该条件
 * GXModelQueryParamDto queryParam = new GXModelQueryParamDto();
 * queryParam.addCondition(condition);
 * List<UserEntity> users = userMapper.selectByCondition(queryParam);
 * 
 * // 4. 也可以与其他条件组合使用
 * queryParam.addCondition(new GXConditionEQ("u", "is_active", 1));
 * </pre>
 * </p>
 * 
 * <p>
 * 性能优化：
 * <ul>
 *   <li>使用参数化查询允许数据库缓存执行计划，提高性能</li>
 *   <li>根据运行环境自动调整IN子句参数数量限制</li>
 *   <li>使用StringBuilder构建参数占位符列表，减少字符串连接开销</li>
 * </ul>
 * </p>
 * 
 * @author 塵渊
 */
public class GXConditionStrIn extends GXCondition<String> {
    /**
     * 存储IN条件的字符串值集合
     */
    private final Set<String> values;

    /**
     * 构造函数
     * 
     * @param tableNameAlias 表别名，如"u"、"user"等，可以为空
     * @param fieldName 字段名，如"role"、"type"等
     * @param value 字符串值集合，用于IN条件
     */
    public GXConditionStrIn(String tableNameAlias, String fieldName, Set<String> value) {
        super(tableNameAlias, fieldName, value);
        this.values = value;
    }

    /**
     * 获取操作符
     * 
     * @return 返回"in"操作符
     */
    @Override
    public String getOp() {
        return "in";
    }

    /**
     * 生成WHERE子句字符串
     * <p>
     * 该方法生成参数化的IN条件SQL片段，格式为：
     * [tableAlias].[fieldName] IN (#{param1}, #{param2}, ...)
     * 每个参数都会被单独处理，确保安全。
     * </p>
     * 
     * @return 返回构建好的WHERE子句字符串
     * @throws GXBusinessException 当IN条件中的值数量超过限制时抛出
     */
    @Override
    public String whereString() {
        // 获取当前运行环境
        String activeProfile = GXCommonUtils.getActiveProfile();
        // 默认限制条件，生产环境使用
        int limitCnt = 100000;
        // 开发环境列表
        List<String> envLst = CollUtil.newArrayList(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        // 如果是开发环境，使用更严格的限制
        if (CollUtil.contains(envLst, activeProfile)) {
            limitCnt = GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, 50);
        }
        // 检查值数量是否超过限制
        if (CollUtil.size(values) > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("IN查询条件不能超过{}条数据!", limitCnt));
        }

        // 构建参数化的IN子句
        List<String> paramPlaceholders = new ArrayList<>();
        int index = 0;
        for (String ignored : values) {
            // 为每个值创建唯一的参数名
            String itemParamName = paramName + "_" + index++;
            // 添加参数占位符
            paramPlaceholders.add("#{dbQueryParamInnerDto.paramMap." + itemParamName + "}");
        }

        // 将所有参数占位符连接成IN子句
        String inClause = String.join(",", paramPlaceholders);

        // 根据是否有表别名构建完整的条件语句
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} ({})", getFieldExpression(), getOp(), inClause);
        }
        return CharSequenceUtil.format("{}.{} {} ({})", tableNameAlias, getFieldExpression(), getOp(), inClause);
    }

    /**
     * 获取字段值并填充参数映射
     * <p>
     * 该方法处理参数映射，为每个值创建单独的参数，并进行SQL注入检查。
     * 虽然方法返回空字符串，但会填充paramMap用于MyBatis参数化查询。
     * </p>
     * 
     * @return 空字符串，实际值存储在paramMap中
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        // 清除原来的参数映射，因为IN条件需要特殊处理
        this.paramMap.clear();
        // 为每个值创建单独的参数
        int index = 0;
        for (String str : values) {
            // 检查SQL注入
            if (GXDBStringEscapeUtils.check(str)) {
                throw new GXSqlInjectionException("SQL注入异常");
            }
            // 创建参数名并存储值
            String itemParamName = paramName + "_" + index++;
            this.paramMap.put(itemParamName, str);
        }
        return "";
    }
}
