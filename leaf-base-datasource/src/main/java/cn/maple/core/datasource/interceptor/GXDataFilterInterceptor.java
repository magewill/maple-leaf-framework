package cn.maple.core.datasource.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
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
 * @author 塵渊 britton@126.com
 * @deprecated 推荐使用MyBatis-Plus官方的DataPermissionInterceptor
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
     * 使用JSqlParser解析原SQL，并添加数据过滤条件。该方法通过解析SQL语句的抽象语法树，
     * 在保持原SQL结构的基础上，安全地添加数据权限过滤条件。
     * </p>
     *
     * @param originalSql 原始SQL语句，不能为null
     * @param scope       数据过滤条件对象，包含SQL过滤条件
     * @return 添加了数据过滤条件的SQL语句
     */
    private String getSelect(String originalSql, GXDataFilterInnerDto scope) {
        if (CharSequenceUtil.isBlank(originalSql)) {
            log.warn("原始SQL为空，无法应用数据权限过滤");
            return originalSql;
        }

        if (scope == null || CharSequenceUtil.isEmpty(scope.getSqlFilter())) {
            log.warn("数据过滤条件为空，返回原SQL");
            return originalSql;
        }

        String sqlFilter = scope.getSqlFilter();
        log.debug("准备添加数据权限过滤条件: {}", sqlFilter);

        try {
            // 解析SQL语句为抽象语法树
            Select select = (Select) CCJSqlParserUtil.parse(originalSql);
            // 注意：若涉及UNION等复杂查询，这里可能不直接是PlainSelect，严格方案应处理SetOperationList
            PlainSelect plainSelect = select.getPlainSelect();

            if (plainSelect == null) {
                log.error("不支持的查询结构(可能是UNION等复杂查询)，为防止数据越权，拒绝执行");
                throw new UnsupportedOperationException("数据权限过滤暂不支持UNION等复杂查询结构，为防数据越权已拦截请求");
            }

            // 获取原WHERE条件
            Expression expression = plainSelect.getWhere();
            
            // 将过滤条件作为正确的 SQL 表达式解析，而不是作为字符串常量包裹
            Expression filterExpression = CCJSqlParserUtil.parseCondExpression(sqlFilter);

            // 如果原SQL没有WHERE条件，直接设置过滤条件
            if (expression == null) {
                log.debug("原SQL没有WHERE条件，直接添加过滤条件");
                plainSelect.setWhere(new ParenthesedExpressionList<>(filterExpression));
            }
            // 如果原SQL有WHERE条件，使用AND连接原条件和过滤条件
            else {
                log.debug("原SQL已有WHERE条件，使用AND连接过滤条件");
                AndExpression andExpression = new AndExpression(new ParenthesedExpressionList<>(expression), new ParenthesedExpressionList<>(filterExpression));
                plainSelect.setWhere(andExpression);
            }

            // 直接返回生成的SQL，无需进行容易出错的黑科技字符串替换
            String resultSql = select.toString();
            log.debug("应用数据权限过滤后的SQL: {}", resultSql);
            return resultSql;
        } catch (JSQLParserException e) {
            // 解析失败时拦截执行，防止越权
            log.error("SQL解析失败，无法应用数据权限过滤: {}, 原因: {}", e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "未知");
            throw new RuntimeException("SQL解析失败，无法应用数据权限过滤，为防止数据越权已拦截请求", e);
        }
    }
}