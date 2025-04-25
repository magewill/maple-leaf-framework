package cn.maple.core.datasource.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Objects;

/**
 * 数据权限过滤拦截器
 * <p>
 * 该拦截器实现了MyBatis-Plus的InnerInterceptor接口，用于在SQL执行前动态添加数据权限过滤条件。
 * 配合@GXDataFilter注解使用，实现基于用户权限的数据行级访问控制。
 * </p>
 *
 * <p>工作原理：</p>
 * <p>1. 通过ThreadLocal获取当前线程的数据过滤条件</p>
 * <p>2. 使用JSqlParser解析原始SQL语句</p>
 * <p>3. 将数据过滤条件动态添加到SQL的WHERE子句中</p>
 * <p>4. 重写原始SQL语句</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在Spring配置中注册拦截器
 * @Configuration
 * public class MybatisPlusConfig {
 *     @Bean
 *     public MybatisPlusInterceptor mybatisPlusInterceptor() {
 *         MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
 *         // 添加数据过滤拦截器
 *         interceptor.addInnerInterceptor(new GXDataFilterInterceptor());
 *         return interceptor;
 *     }
 * }
 *
 * // 2. 在Service方法上使用@GXDataFilter注解
 * @GXDataFilter(tableAlias = "t", userIdFieldNames = {"creator_id"})
 * public List<UserEntity> getUserList(Map<String, Object> params) {
 *     return baseDao.selectByMap(params);
 * }
 * </pre>
 *
 * <p>线程安全说明：</p>
 * <p>本拦截器是线程安全的，因为：</p>
 * <p>1. 不维护任何可变状态</p>
 * <p>2. 通过ThreadLocal隔离不同线程的数据</p>
 * <p>3. 所有操作都基于方法参数</p>
 *
 * @author 塵渊 britton@126.com
 */
@Slf4j
public class GXDataFilterInterceptor implements InnerInterceptor {
    /**
     * 查询前拦截方法
     * <p>
     * 在MyBatis执行查询前调用，用于添加数据权限过滤条件
     * </p>
     *
     * @param executor      MyBatis执行器
     * @param ms            映射语句对象
     * @param parameter     参数对象
     * @param rowBounds     分页参数
     * @param resultHandler 结果处理器
     * @param boundSql      绑定的SQL对象
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        // 获取当前线程的数据过滤条件
        GXDataFilterInnerDto scope = getDataScope();
        // 如果没有数据过滤条件或过滤SQL为空，则不进行数据过滤
        if (Objects.isNull(scope) || CharSequenceUtil.isBlank(scope.getSqlFilter())) {
            return;
        }

        // 根据原SQL和过滤条件构建新SQL
        String buildSql = getSelect(boundSql.getSql(), scope);

        // 使用MyBatis-Plus工具类重写原SQL
        PluginUtils.mpBoundSql(boundSql).sql(buildSql);
        log.info("已应用数据权限过滤，重写SQL完成");
    }

    /**
     * 获取数据过滤条件
     * <p>
     * 从ThreadLocal中获取当前线程的数据过滤条件
     * </p>
     *
     * @return 数据过滤条件对象，如果不存在则返回null
     */
    private GXDataFilterInnerDto getDataScope() {
        // 从ThreadLocal中获取数据过滤条件
        GXDataFilterInnerDto dataScope = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
        if (Objects.nonNull(dataScope)) {
            return dataScope;
        }

        return null;
    }

    /**
     * 构建包含数据过滤条件的SQL
     * <p>
     * 使用JSqlParser解析原SQL，并添加数据过滤条件
     * </p>
     *
     * @param originalSql 原始SQL语句
     * @param scope       数据过滤条件对象
     * @return 添加了数据过滤条件的SQL语句
     */
    private String getSelect(String originalSql, GXDataFilterInnerDto scope) {
        try {
            // 解析SQL语句
            Select select = (Select) CCJSqlParserUtil.parse(originalSql);
            PlainSelect plainSelect = select.getPlainSelect();

            // 获取原WHERE条件
            Expression expression = plainSelect.getWhere();
            // 创建数据过滤条件
            StringValue stringValue = new StringValue("'" + scope.getSqlFilter() + "'");

            // 如果原SQL没有WHERE条件，直接设置过滤条件
            if (expression == null) {
                plainSelect.setWhere(stringValue);
            }
            // 如果原SQL有WHERE条件，使用AND连接原条件和过滤条件
            else {
                AndExpression andExpression = new AndExpression(expression, stringValue);
                plainSelect.setWhere(andExpression);
            }

            // 处理特殊占位符并返回最终SQL
            return select.toString().replace("'$$'", "");
        } catch (JSQLParserException e) {
            // 解析失败时返回原SQL，确保查询能够继续执行
            log.warn("SQL解析失败，无法应用数据权限过滤: {}", e.getMessage());
            return originalSql;
        }
    }
}