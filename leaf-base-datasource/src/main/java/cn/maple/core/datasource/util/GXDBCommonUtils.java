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
import java.util.stream.Collectors;

@SuppressWarnings({"unused"})
public class GXDBCommonUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXDBCommonUtils.class);

    private GXDBCommonUtils() {
    }

    /**
     * 给现有查询条件新增查询条件
     * <p>
     * 该方法对key和value参数进行安全检查，防止SQL注入攻击。
     * 如果检测到潜在的SQL注入，将抛出GXSqlInjectionException异常。
     * </p>
     *
     * @param requestParam       请求参数
     * @param key                添加的key
     * @param value              添加的value
     * @param returnRequestParam 是否返回requestParam
     * @return Dict
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
     * 给现有查询条件新增查询条件
     * <p>
     * 该方法对sourceData中的键值对进行安全检查，防止SQL注入攻击。
     * 如果检测到潜在的SQL注入，将抛出GXSqlInjectionException异常。
     * </p>
     *
     * @param requestParam       请求参数
     * @param sourceData         需要添加的map
     * @param returnRequestParam 是否返回requestParam
     * @return Dict
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
     * 获取实体的表名字
     *
     * @param clazz Class
     * @return String
     */
    public static String getTableName(Class<?> clazz) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(clazz);
        return tableInfo.getTableName();
    }

    /**
     * 获取分页对象信息
     *
     * @param page    分页对象
     * @param records 分页数据
     * @return GXPagination
     */
    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page, List<R> records) {
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(records, totalCount, pages, pageSize, currentPage);
    }

    /**
     * 获取分页对象信息
     *
     * @param page 分页对象
     * @return GXPagination
     */
    public static <R> GXPaginationResDto<R> convertPageToPaginationResDto(IPage<R> page) {
        long pages = page.getPages();
        long currentPage = page.getCurrent();
        long pageSize = page.getSize();
        long totalCount = page.getTotal();
        return new GXPaginationResDto<>(page.getRecords(), totalCount, pages, pageSize, currentPage);
    }

    /**
     * 组合JSON搜索条件
     * <p>
     * 该方法对searchField和searchExpression参数进行安全检查，防止SQL注入攻击。
     * 同时对searchValue中的字符串值进行安全处理。
     * 如果检测到潜在的SQL注入，将抛出GXSqlInjectionException异常。
     * </p>
     * <pre>
     *     {@code
     *     compositeJSONSearchExpression("custer_info" , "emp[*].name" , CollUtil.newHashSet("jack"));
     *     compositeJSONSearchExpression("label_id" , "" , CollUtil.newHashSet(1));
     *     }
     * </pre>
     *
     * @param searchField      需要搜索的字段
     * @param searchExpression 表达式
     * @param searchValue      搜索的值
     * @return 搜索表达式
     * @throws GXBusinessException     如果参数为空
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
     * 获取SELECT　SQL语句
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return String
     */
    public static String getSelectSql(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return GXBaseBuilder.findByCondition(dbQueryParamInnerDto);
    }

    /**
     * 获取SELECT　SQL语句
     *
     * @param dbQueryParamInnerDto 查询条件
     * @return SQL
     */
    public static String getSelectOneSql(GXBaseQueryParamInnerDto dbQueryParamInnerDto) {
        return GXBaseBuilder.findOneByCondition(dbQueryParamInnerDto);
    }

    /**
     * 设置更新条件对象的值
     *
     * @param condition 条件
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
     * 构造分页对象
     *
     * @param page     当前页
     * @param pageSize 每页大小
     * @return 分页对象
     */
    public static <R> IPage<R> constructPageObject(Integer page, Integer pageSize) {
        return constructPageObject(page, pageSize, true);
    }

    /**
     * 构造分页对象
     *
     * @param page        当前页
     * @param pageSize    每页大小
     * @param searchCount 是否使用MyBatis Plus提供的count(*)
     * @return 分页对象
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
     *
     * @param sql       SQL对象
     * @param condition 条件
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
            String safeColumn = GXBaseBuilder.safeColumnName(column);

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
}
