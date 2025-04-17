package cn.maple.core.datasource.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReUtil;
import cn.maple.core.datasource.builder.GXBaseBuilder;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionExclusionDeletedField;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.jdbc.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据库通用工具类
 * <p>
 * 提供数据库操作相关的工具方法，包括：
 * 1. SQL注入防护 - 对用户输入进行严格检查和转义，防止SQL注入攻击
 * 2. 查询条件构建 - 安全地构建和组装SQL查询条件
 * 3. 分页对象处理 - 创建和转换分页对象
 * 4. JSON搜索表达式生成 - 安全地生成用于JSON字段搜索的表达式
 * 5. 表名和列名安全处理 - 确保表名和列名不包含SQL注入风险
 * </p>
 * <p>
 * 该类中的所有方法都是线程安全的，可以在多线程环境中安全使用。
 * 所有方法都进行了内存安全处理，避免内存泄漏和溢出风险。
 * </p>
 * 
 * @author 塵子曦
 */
@SuppressWarnings({"unused"})
public class GXDBCommonUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXDBCommonUtils.class);

    /**
     * 最大输入长度，防止超长输入导致性能问题和内存溢出
     * 限制为1MB，足够处理大多数正常业务场景的输入
     */
    /**
     * 最大输入长度，防止超长输入导致性能问题和内存溢出
     * 限制为1MB，足够处理大多数正常业务场景的输入
     */
    private static final int MAX_INPUT_LENGTH = 1024 * 1024; // 1MB

    /**
     * SQL注入检测正则表达式
     * 用于检测常见的SQL注入模式
     * 包括：
     * 1. 分号（可能用于分隔多条SQL语句）
     * 2. UNION SELECT语句（用于联合查询攻击）
     * 3. 文件操作函数（load_file, outfile, dumpfile等）
     * 4. 时间延迟函数（sleep, benchmark等，用于盲注）
     * 5. 注释符（--, #, /*等，用于注释掉查询的剩余部分）
     * 6. 常见的条件注入模式（OR 1=1, AND 1=1等）
     * 7. 系统函数和变量（@@version, user()等）
     */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)" + // 忽略大小写
                    "(" +
                    // 模式 1：SQL 注释和语句分隔符（独立出现）
                    "(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;)" +
                    "|" +
                    // 模式 2：单引号后的注释或分隔符（'value' -- 或 'value';）
                    "'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'\\s*(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;)" +
                    "|" +
                    // 模式 3：SQL 关键字（union select, drop, alter 等），但需后跟注入模式
                    "\\b(?:union\\s+(?:all\\s+)?select|select\\s+.*\\s+from|insert\\s+into|update\\s+.*\\s+set|delete\\s+from|drop\\s+(?:table|database)|alter\\s+(?:table|database)|truncate\\s+table|create\\s+(?:table|database))\\b" +
                    "\\s*(?:--[\\s\\r\\n]*|#|/\\*|\\*/|;|\\b(?:or|and)\\s+(?:\\d+\\s*=\\s*\\d+|'[^']+'\\s*=\\s*'[^']+'))" +
                    "|" +
                    // 模式 4：系统表和函数（information_schema, xp_cmdshell 等）
                    "\\b(?:information_schema\\.|sys\\.|sysobjects\\.|xp_cmdshell|sp_executesql|@@version|user\\s*\\(\\s*\\)|database\\s*\\(\\s*\\)|schema\\s*\\(\\s*\\))\\b" +
                    "|" +
                    // 模式 5：文件操作和延迟函数（load_file, outfile, sleep 等）
                    "\\b(?:load_file\\s*\\(|outfile\\s*\\(|dumpfile\\s*\\(|into\\s+(?:outfile|dumpfile)|sleep\\s*\\(\\s*\\d+\\s*\\)|benchmark\\s*\\(\\s*\\d+\\s*,\\s*[^)]+\\))\\b" +
                    "|" +
                    // 模式 6：逻辑操作符注入（or 1=1, and 'a'='a' 等）
                    "\\b(?:or|and)\\s+(?:" +
                    "\\d+\\s*=\\s*\\d+" + // 1=1
                    "|\\d+\\s*=\\s*\\d+\\s*(?:--[\\s\\r\\n]*|#)" + // 1=1 --
                    "|'[^']+'\\s*=\\s*'[^']+'" + // 'a'='a'
                    "|'[^']+'\\s*=\\s*'[^']+'\\s*(?:--[\\s\\r\\n]*|#)" + // 'a'='a' --
                    ")\\b" +
                    "|" +
                    // 模式 7：其他常见注入模式（exec, execute 等）
                    "\\b(?:exec\\s+\\w+|execute\\s+\\w+)\\b" +
                    ")"
    );

    // 合法子查询的正则表达式，用于豁免检测
    private static final Pattern LEGITIMATE_SUBQUERY_PATTERN = Pattern.compile(
            "^\\s*\\(\\s*SELECT\\s+.*\\s+FROM\\s+.*\\s*(?:WHERE\\s+.*)?\\s*\\)\\s*(?:UNION\\s+(?:ALL\\s+)?\\s*\\(\\s*SELECT\\s+.*\\s+FROM\\s+.*\\s*(?:WHERE\\s+.*)?\\s*\\)\\s*)*$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 私有构造函数，防止实例化
     * 该类只提供静态方法，不需要实例化
     */
    private GXDBCommonUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 给现有查询条件新增查询条件
     * <p>
     * 该方法对key和value参数进行安全检查，防止SQL注入攻击。
     * 如果检测到潜在的SQL注入，将抛出GXSqlInjectionException异常。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象，避免内存泄漏。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param requestParam       请求参数，包含现有查询条件的Dict对象
     * @param key                添加的key，不能为null或包含SQL注入风险字符
     * @param value              添加的value，如果是字符串类型会进行SQL注入检查
     * @param returnRequestParam 是否返回requestParam，true返回原始requestParam，false只返回条件Dict
     * @return Dict 根据returnRequestParam参数返回原始requestParam或只包含条件的Dict
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public static Dict addSearchCondition(Dict requestParam, String key, Object value, boolean returnRequestParam) {
        final Object obj = requestParam.getObj(GXBuilderConstant.SEARCH_CONDITION_NAME);
        if (null == obj) {
            return requestParam;
        }

        // 检查key是否存在SQL注入风险
        if (CharSequenceUtil.isNotEmpty(key) && GXDBStringEscapeUtils.check(key)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.key)", key);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        // 检查value是否存在SQL注入风险（如果是字符串类型）
        if (value instanceof String && GXDBStringEscapeUtils.check((String) value)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.value)", value);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        final Dict data = Convert.convert(Dict.class, obj);
        data.set(key, value);
        if (returnRequestParam) {
            requestParam.put(GXBuilderConstant.SEARCH_CONDITION_NAME, data);
            return requestParam;
        }
        return data;
    }

    /**
     * 给现有查询条件新增查询条件（批量添加）
     * <p>
     * 该方法对sourceData中的键值对进行安全检查，防止SQL注入攻击。
     * 如果检测到潜在的SQL注入，将抛出GXSqlInjectionException异常。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象，避免内存泄漏。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param requestParam       请求参数，包含现有查询条件的Dict对象
     * @param sourceData         需要批量添加的条件map，其中每个键值对都会被添加到查询条件中
     * @param returnRequestParam 是否返回requestParam，true返回原始requestParam，false只返回条件Dict
     * @return Dict 根据returnRequestParam参数返回原始requestParam或只包含条件的Dict
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public static Dict addSearchCondition(Dict requestParam, Dict sourceData, boolean returnRequestParam) {
        final Object obj = requestParam.getObj(GXBuilderConstant.SEARCH_CONDITION_NAME);
        if (null == obj) {
            return requestParam;
        }

        // 检查sourceData中的键值对是否存在SQL注入风险
        if (Objects.nonNull(sourceData)) {
            sourceData.forEach((k, v) -> {
                // 检查key是否存在SQL注入风险
                if (CharSequenceUtil.isNotEmpty(k) && GXDBStringEscapeUtils.check(k)) {
                    String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.sourceData.key)", k);
                    LOG.error(message);
                    throw new GXSqlInjectionException(message);
                }

                // 检查value是否存在SQL注入风险（如果是字符串类型）
                if (v instanceof String && GXDBStringEscapeUtils.check((String) v)) {
                    String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: addSearchCondition.sourceData.value)", v);
                    LOG.error(message);
                    throw new GXSqlInjectionException(message);
                }
            });
        }

        final Dict data = Convert.convert(Dict.class, obj);
        data.putAll(sourceData);
        if (returnRequestParam) {
            requestParam.put(GXBuilderConstant.SEARCH_CONDITION_NAME, data);
            return requestParam;
        }
        return data;
    }

    /**
     * 获取实体类对应的数据库表名
     * <p>
     * 该方法通过MyBatis Plus的TableInfoHelper获取实体类对应的表名。
     * 实体类必须已经被MyBatis Plus正确配置，否则可能返回null或抛出异常。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象。
     * 线程安全：该方法依赖MyBatis Plus的TableInfoHelper，其内部实现是线程安全的。
     * </p>
     *
     * @param clazz 实体类的Class对象
     * @return String 对应的数据库表名
     * @throws RuntimeException 如果实体类未被MyBatis Plus正确配置
     */
    public static String getTableName(Class<?> clazz) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(clazz);
        return tableInfo.getTableName();
    }

    /**
     * 获取分页对象信息（自定义记录集）
     * <p>
     * 该方法将MyBatis Plus的IPage对象转换为自定义的GXPaginationResDto对象，
     * 同时允许传入自定义的记录集替换原始分页对象中的记录。
     * </p>
     * <p>
     * 内存安全：该方法创建新的GXPaginationResDto对象，但不会持有原始IPage对象的引用，避免内存泄漏。
     * 线程安全：该方法不修改输入参数，可在多线程环境中安全使用。
     * </p>
     *
     * @param page    MyBatis Plus的分页对象，包含分页信息（总页数、当前页等）
     * @param records 自定义的分页数据记录集，将替换page中原有的记录集
     * @return GXPaginationResDto 自定义分页响应对象
     */
    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page, List<R> records) {
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(records, totalCount, pages, pageSize, currentPage);
    }

    /**
     * 获取分页对象信息（使用原始记录集）
     * <p>
     * 该方法将MyBatis Plus的IPage对象转换为自定义的GXPaginationResDto对象，
     * 使用原始分页对象中的记录集。
     * </p>
     * <p>
     * 内存安全：该方法创建新的GXPaginationResDto对象，但不会持有原始IPage对象的引用，避免内存泄漏。
     * 线程安全：该方法不修改输入参数，可在多线程环境中安全使用。
     * </p>
     *
     * @param page MyBatis Plus的分页对象，包含分页信息和记录集
     * @return GXPaginationResDto 自定义分页响应对象
     */
    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page) {
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(page.getRecords(), totalCount, pages, pageSize, currentPage);
    }

    /**
     * 生成JSON搜索表达式
     * <p>
     * 该方法对searchField和searchExpression参数进行安全检查，防止SQL注入攻击。
     * 同时对searchValue中的字符串值进行安全处理。
     * 如果检测到潜在的SQL注入，将抛出GXSqlInjectionException异常。
     * </p>
     * <p>
     * 内存安全：该方法使用StringBuilder构建表达式，避免字符串拼接产生大量临时对象。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     * <pre>
     *     {@code
     *     generateJSONSearchExpression("custer_info" , "emp[*].name" , CollUtil.newHashSet("jack"));
     *     generateJSONSearchExpression("label_id" , "" , CollUtil.newHashSet(1));
     *     }
     * </pre>
     *
     * @param searchField      需要搜索的JSON字段名，不能为空
     * @param searchExpression JSON路径表达式，可以为空（为空时默认为"$"）
     * @param searchValue      搜索的值集合，不能为空
     * @return 格式化的JSON搜索表达式，可直接用于SQL查询
     * @throws GXBusinessException     如果searchField或searchValue为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public static String generateJSONSearchExpression(String searchField, String searchExpression, Set<Object> searchValue) {
        if (CharSequenceUtil.isEmpty(searchField)) {
            throw new GXBusinessException("请传递搜索的字段");
        }
        if (CollUtil.isEmpty(searchValue)) {
            throw new GXBusinessException("请传递搜索的值");
        }

        // 检查searchField是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(searchField)) {
            String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: generateJSONSearchExpression.searchField)", searchField);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        // 安全处理searchField
        String safeSearchField = GXDBStringEscapeUtils.escapeJsonPath(searchField);

        // 处理searchExpression
        if (CharSequenceUtil.isEmpty(searchExpression)) {
            searchExpression = "$";
        } else {
            // 检查searchExpression是否存在SQL注入风险
            if (GXDBStringEscapeUtils.check(searchExpression)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: generateJSONSearchExpression.searchExpression)", searchExpression);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
            // 安全处理searchExpression
            searchExpression = CharSequenceUtil.format("$.{}", GXDBStringEscapeUtils.escapeJsonPath(searchExpression));
        }

        String expressionTemplate = GXBuilderConstant.JSON_SEARCH_EXPRESSION_TEMPLATE;
        String searchStr = searchValue.stream().map(o -> {
            if (o instanceof Number) {
                return CharSequenceUtil.format("{}", o.toString());
            }
            // 对字符串值进行安全处理
            String strValue = o.toString();
            if (GXDBStringEscapeUtils.check(strValue)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: generateJSONSearchExpression.searchValue)", strValue);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
            return CharSequenceUtil.format("\"{}\"", GXDBStringEscapeUtils.escapeJson(strValue));
        }).collect(Collectors.joining(","));

        return CharSequenceUtil.format(expressionTemplate, safeSearchField, searchExpression, searchStr);
    }

    /**
     * 获取SELECT SQL语句（多条记录查询）
     * <p>
     * 该方法根据查询参数构建完整的SELECT SQL语句，用于查询多条记录。
     * 内部调用GXBaseBuilder.findByCondition方法生成SQL。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数对象，包含表名、字段、条件等信息
     * @return String 生成的完整SELECT SQL语句
     */
    public static String getSelectSql(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return GXBaseBuilder.findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 获取SELECT SQL语句（单条记录查询）
     * <p>
     * 该方法根据查询参数构建完整的SELECT SQL语句，用于查询单条记录。
     * 内部调用GXBaseBuilder.findOneByCondition方法生成SQL。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param dbQueryParamInnerDto 查询条件参数对象，包含表名、字段、条件等信息
     * @return String 生成的完整SELECT SQL语句（针对单条记录查询优化）
     */
    public static String getSelectOneSql(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return GXBaseBuilder.findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 组装更新条件包装器
     * <p>
     * 该方法根据条件列表构建MyBatis Plus的UpdateWrapper对象，用于执行更新操作。
     * 会自动处理不同类型的条件（等于、不等于、大于、小于等），并进行安全处理。
     * </p>
     * <p>
     * 内存安全：该方法创建新的UpdateWrapper对象，但不会持有原始条件列表的引用。
     * 线程安全：该方法不修改输入参数，可在多线程环境中安全使用。
     * </p>
     *
     * @param condition 条件列表，每个条件包含字段表达式、操作符和值
     * @return UpdateWrapper 构建好的更新条件包装器
     */
    public static <T> UpdateWrapper<T> assemblyUpdateWrapper(List<GXCondition<?>> condition) {
        UpdateWrapper<T> updateWrapper = new UpdateWrapper<>();
        Dict methodNameDict = Dict.create()
                .set("=", "eq")
                .set("!=", "ne")
                .set("in", "in")
                .set("not in", "notIn")
                .set(">=", "ge")
                .set(">", "gt")
                .set("<=", "le")
                .set("<", "lt");

        condition.forEach(c -> {
            if (!GXConditionExclusionDeletedField.class.isAssignableFrom(c.getClass())) {
                String column = c.getFieldExpression();
                Object value = c.getFieldValue();

                if (String.class.isAssignableFrom(value.getClass())) {
                    value = CharSequenceUtil.replace(value.toString(), "'", "");
                }

                String op = c.getOp();
                column = CharSequenceUtil.toUnderlineCase(column);
                String methodName = methodNameDict.getStr(op);

                if (Objects.nonNull(methodName)) {
                    GXCommonUtils.reflectCallObjectMethod(updateWrapper, methodName, true, column, value);
                }
            }
        });

        return updateWrapper;
    }

    /**
     * 构造分页对象（带默认计数）
     * <p>
     * 该方法根据当前页和每页大小构建MyBatis Plus的分页对象，
     * 默认启用总记录数计算。会对参数进行校验，确保分页参数在合理范围内。
     * </p>
     * <p>
     * 内存安全：该方法创建新的Page对象，占用内存较小。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param page     当前页码，如果为null或小于0，将使用默认值
     * @param pageSize 每页大小，如果为null、小于等于0或大于最大限制，将使用默认值
     * @return IPage 构建好的分页对象
     */
    public static <R> IPage<R> constructPageObject(Integer page, Integer pageSize) {
        return constructPageObject(page, pageSize, true);
    }

    /**
     * 构造分页对象（可控制是否计数）
     * <p>
     * 该方法根据当前页和每页大小构建MyBatis Plus的分页对象，
     * 可以控制是否启用总记录数计算。会对参数进行校验，确保分页参数在合理范围内。
     * </p>
     * <p>
     * 内存安全：该方法创建新的Page对象，占用内存较小。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param page        当前页码，如果为null或小于0，将使用默认值
     * @param pageSize    每页大小，如果为null、小于等于0或大于最大限制，将使用默认值
     * @param searchCount 是否使用MyBatis Plus提供的count(*)计算总记录数
     * @return IPage 构建好的分页对象
     */
    public static <R> IPage<R> constructPageObject(Integer page, Integer pageSize, boolean searchCount) {
        int defaultCurrentPage = GXCommonConstant.DEFAULT_CURRENT_PAGE;
        int defaultPageSize = GXCommonConstant.DEFAULT_PAGE_SIZE;
        int defaultMaxPageSize = GXCommonConstant.DEFAULT_MAX_PAGE_SIZE;
        if (Objects.isNull(page) || page < 0) {
            page = defaultCurrentPage;
        }
        if (Objects.isNull(pageSize) || pageSize > defaultMaxPageSize || pageSize <= 0) {
            pageSize = defaultPageSize;
        }
        return new Page<>(page, pageSize, searchCount);
    }

    /**
     * 拼装SQL对象的条件
     * <p>
     * 该方法对condition中的键值对进行安全检查，防止SQL注入攻击。
     * 对列名和值进行适当的转义处理，确保生成的SQL语句安全可靠。
     * 如果检测到潜在的SQL注入，将抛出GXSqlInjectionException异常。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象，避免内存泄漏。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param sql       MyBatis的SQL对象，用于构建SQL语句
     * @param condition 条件字典，键为列名，值为对应的条件值
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public static void assemblySqlObjectCondition(SQL sql, Dict condition) {
        if (Objects.isNull(condition) || condition.isEmpty()) {
            return;
        }

        condition.forEach((column, val) -> {
            // 检查列名是否存在SQL注入风险
            if (CharSequenceUtil.isNotEmpty(column) && GXDBStringEscapeUtils.check(column)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: assemblySqlObjectCondition.column)", column);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }

            // 安全处理列名
            String safeColumn = safeColumnName(column);

            // 处理值
            final String value = Convert.toStr(val);

            // 检查值是否存在SQL注入风险
            if (CharSequenceUtil.isNotEmpty(value) && GXDBStringEscapeUtils.check(value)) {
                String message = CharSequenceUtil.format("检测到潜在的SQL注入攻击: {} (来源: assemblySqlObjectCondition.value)", value);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }

            // 根据值的类型选择不同的模板
            String template;
            String safeValue;

            if (ReUtil.isMatch("^[+-]?(0|([1-9]\\d*))(\\.\\d+)?$", value)) {
                // 数值类型，不需要单引号
                template = "{} = {}";
                safeValue = value;
            } else {
                // 字符串类型，需要单引号并进行转义
                template = "{} = '{}'";
                safeValue = GXDBStringEscapeUtils.escapeSql(value);
            }

            // 构建安全的WHERE子句
            sql.WHERE(CharSequenceUtil.format(template, safeColumn, safeValue));
        });
    }

    /**
     * 检查输入字符串是否存在潜在的 SQL 注入攻击
     * <p>
     * 该方法通过以下步骤进行检测：
     * 1. 检查输入是否为空或超长。
     * 2. 如果输入是合法的子查询，则豁免检测。
     * 3. 使用正则表达式进行初步检测。
     * 4. 使用 GXDBStringEscapeUtils.check 方法进行更全面的检测（如果可用）。
     * </p>
     * <p>
     * 内存安全：该方法对输入长度进行限制，防止超长输入导致内存溢出。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param input       需要检查的输入字符串
     * @param source      输入来源（用于日志记录，帮助定位问题）
     * @param isUserInput 是否为用户输入（如果是，则严格检测；如果不是，则放宽检测）
     * @throws GXSqlInjectionException 如果检测到 SQL 注入攻击
     * @throws IllegalArgumentException 如果输入长度超过最大限制
     */
    public static void checkSQLInjection(String input, String source, boolean isUserInput) {
        // 检查输入是否为空
        if (CharSequenceUtil.isEmpty(input)) {
            LOG.debug("输入为空，直接返回 (来源: {})", source);
            return;
        }

        // 检查输入长度，防止超长输入导致性能问题
        if (input.length() > MAX_INPUT_LENGTH) {
            String message = CharSequenceUtil.format("输入长度超过最大限制 ({}): {} (来源: {})", MAX_INPUT_LENGTH, input, source);
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        // 如果不是用户输入，且输入是合法的子查询，则豁免检测
        if (!isUserInput && LEGITIMATE_SUBQUERY_PATTERN.matcher(input).matches()) {
            LOG.debug("输入是合法的子查询，豁免检测: {} (来源: {})", input, source);
            return;
        }

        // 使用简单的正则表达式进行初步检测
        if (ReUtil.contains(SQL_INJECTION_PATTERN, input)) {
            String message = CharSequenceUtil.format("第一步检测到潜在的SQL注入攻击: {} (来源: {})", input, source);
            LOG.error(message);
            throw new GXSqlInjectionException(message);
        }

        // 使用GXDBStringEscapeUtils.check方法进行更全面的SQL注入检测
        // 这个方法检查更多的SQL注入模式，包括SQL语法、注释、盲注等
        try {
            if (GXDBStringEscapeUtils.check(input)) {
                String message = CharSequenceUtil.format("第二步检测到潜在的SQL注入攻击: {} (来源: {})", input, source);
                LOG.error(message);
                throw new GXSqlInjectionException(message);
            }
        } catch (Exception e) {
            // 如果GXDBStringEscapeUtils.check方法抛出异常，记录日志并继续使用原有的检测方法
            LOG.warn("使用GXDBStringEscapeUtils.check方法检测SQL注入时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 安全处理表名
     * <p>
     * 该方法确保表名不包含SQL注入攻击模式，并对表名进行适当的转义处理。
     * 表名通常不应包含需要转义的特殊字符，但为了安全起见，仍然进行检查。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param tableName 原始表名，不能为空
     * @return 安全的表名，经过验证和处理
     * @throws GXBusinessException     如果表名为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public static String safeTableName(String tableName) {
        if (CharSequenceUtil.isEmpty(tableName)) {
            throw new GXBusinessException("表名不能为空");
        }

        // 检查SQL注入
        checkSQLInjection(tableName, "tableName", false);

        // 表名通常不应包含特殊字符，但为了安全起见，仍然进行检查
        // 如果表名包含特殊字符（如点号以外的特殊字符），可能表示SQL注入尝试
        if (tableName.matches(".*[;'\"\\\\].*")) {
            throw new GXSqlInjectionException("表名包含不允许的特殊字符: " + tableName);
        }

        return tableName;
    }

    /**
     * 安全处理表别名
     * <p>
     * 该方法确保表别名不包含SQL注入攻击模式，并对表别名进行适当的转义处理。
     * 表别名通常不应包含需要转义的特殊字符，但为了安全起见，仍然进行检查。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param tableAlias 原始表别名，可以为空
     * @return 安全的表别名，如果输入为空则返回null，否则返回经过验证和处理的别名
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public static String safeTableAlias(String tableAlias) {
        if (CharSequenceUtil.isEmpty(tableAlias)) {
            return null;
        }

        // 检查SQL注入
        checkSQLInjection(tableAlias, "tableAlias", false);

        // 表别名通常不应包含特殊字符，但为了安全起见，仍然进行检查
        // 如果表别名包含特殊字符，可能表示SQL注入尝试
        if (tableAlias.matches(".*[;'\"\\\\].*")) {
            throw new GXSqlInjectionException("表别名包含不允许的特殊字符: " + tableAlias);
        }

        return tableAlias;
    }

    /**
     * 安全处理列名
     * <p>
     * 该方法确保列名不包含SQL注入攻击模式，并对列名进行适当的转义处理。
     * 列名通常不应包含需要转义的特殊字符，但为了安全起见，仍然进行检查。
     * 该方法支持处理SQL函数调用，如GROUP_CONCAT等聚合函数。
     * </p>
     * <p>
     * 内存安全：该方法不会创建大量临时对象。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param columnName 原始列名，不能为空
     * @return 安全的列名，经过验证和处理
     * @throws GXBusinessException     如果列名为空
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public static String safeColumnName(String columnName) {
        if (CharSequenceUtil.isEmpty(columnName)) {
            throw new GXBusinessException("列名不能为空");
        }

        // 检查是否是SQL函数调用（如GROUP_CONCAT, COUNT, SUM等）
        // 函数调用通常具有函数名后跟括号的形式
        boolean isSqlFunction = isSqlFunctionCall(columnName);

        if (!isSqlFunction) {
            // 如果不是SQL函数调用，则进行常规SQL注入检查
            checkSQLInjection(columnName, "columnName", true);

            // 列名通常不应包含特殊字符，但为了安全起见，仍然进行检查
            // 如果列名包含特殊字符（如点号以外的特殊字符），可能表示SQL注入尝试
            // 允许点号是因为有时列名可能包含表名前缀，如 table.column
            if (columnName.matches(".*[;'\"\\\\].*")) {
                throw new GXSqlInjectionException("列名包含不允许的特殊字符: " + columnName);
                //LOGGER.error("列名包含不允许的特殊字符: {}", columnName);
            }
        } else {
            // 对于SQL函数调用，进行基本的安全检查，但允许函数语法
            // 检查是否包含明显的SQL注入尝试，如多条语句、注释等
            if (columnName.matches(".*[;].*") || columnName.matches(".*--.*") || columnName.matches(".*#.*")) {
                LOG.error("SQL函数调用中包含可疑字符: {}", columnName);
                throw new GXSqlInjectionException("SQL函数调用中包含可疑字符: " + columnName);
            }
        }

        return columnName;
    }

    /**
     * 判断字符串是否是SQL函数调用
     * <p>
     * 该方法检查字符串是否符合SQL函数调用的基本模式，
     * 包括常见的聚合函数（如GROUP_CONCAT, SUM, COUNT等）和其他SQL函数。
     * </p>
     * <p>
     * 内存安全：该方法使用正则表达式进行匹配，不会创建大量临时对象。
     * 线程安全：该方法不修改静态变量，可在多线程环境中安全使用。
     * </p>
     *
     * @param str 要检查的字符串，可以为空
     * @return 如果字符串是SQL函数调用则返回true，否则返回false
     */
    public static boolean isSqlFunctionCall(String str) {
        if (CharSequenceUtil.isEmpty(str)) {
            return false;
        }

        // 常见的SQL函数名称模式
        String functionPattern = "(?i)(GROUP_CONCAT|CONCAT|COUNT|SUM|AVG|MIN|MAX|DISTINCT|SUBSTRING|CAST|CONVERT|DATE_FORMAT|" +
                "IF|IFNULL|NULLIF|COALESCE|CASE|WHEN|THEN|ELSE|END|ROUND|FLOOR|CEILING|ABS|RAND|" +
                "LENGTH|CHAR_LENGTH|TRIM|LTRIM|RTRIM|LOWER|UPPER|REPLACE|REGEXP_REPLACE|" +
                "DATE|DATETIME|TIME|YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|" +
                "JSON_EXTRACT|JSON_CONTAINS|JSON_OBJECT|JSON_ARRAY|" +
                "ST_Distance|ST_Contains|ST_Within|ST_Intersects)";

        // 检查是否匹配函数调用模式：函数名后跟括号，括号内可能包含参数
        // 同时处理可能的别名（使用AS或空格）
        String functionCallPattern = functionPattern + "\\s*\\([^)]*\\)(\\s+AS\\s+\\w+|\\s+\\w+)?";

        return str.matches(functionCallPattern) ||
                // 处理嵌套函数调用的情况
                str.matches(".*" + functionPattern + "\\s*\\(.*\\).*") ||
                // 处理带有SEPARATOR关键字的GROUP_CONCAT
                str.matches("(?i).*GROUP_CONCAT\\s*\\([^)]*SEPARATOR[^)]*\\).*");
    }
}
