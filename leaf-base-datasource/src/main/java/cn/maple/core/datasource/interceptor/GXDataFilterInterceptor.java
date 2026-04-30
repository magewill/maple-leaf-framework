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

@Slf4j
public class GXDataFilterInterceptor implements InnerInterceptor {
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        GXDataFilterInnerDto scope = getDataScope();
        if (Objects.isNull(scope) || CharSequenceUtil.isBlank(scope.getSqlFilter())) {
            return;
        }

        String buildSql = getSelect(boundSql.getSql(), scope);

        PluginUtils.mpBoundSql(boundSql).sql(buildSql);
        log.info("已应用数据权限过滤，重写SQL完成");
    }

    private GXDataFilterInnerDto getDataScope() {
        return GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
    }

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
            Select select = (Select) CCJSqlParserUtil.parse(originalSql);
            PlainSelect plainSelect = select.getPlainSelect();

            if (plainSelect == null) {
                log.error("不支持的查询结构(可能是UNION等复杂查询)，为防止数据越权，拒绝执行");
                throw new UnsupportedOperationException("数据权限过滤暂不支持UNION等复杂查询结构，为防数据越权已拦截请求");
            }

            Expression expression = plainSelect.getWhere();

            Expression filterExpression = CCJSqlParserUtil.parseCondExpression(sqlFilter);

            if (expression == null) {
                log.debug("原SQL没有WHERE条件，直接添加过滤条件");
                plainSelect.setWhere(new ParenthesedExpressionList<>(filterExpression));
            } else {
                log.debug("原SQL已有WHERE条件，使用AND连接过滤条件");
                plainSelect.setWhere(new AndExpression(
                        new ParenthesedExpressionList<>(expression),
                        new ParenthesedExpressionList<>(filterExpression)));
            }

            String resultSql = select.toString();
            log.debug("应用数据权限过滤后的SQL: {}", resultSql);
            return resultSql;
        } catch (JSQLParserException e) {
            log.error("SQL解析失败，无法应用数据权限过滤: {}, 原因: {}", e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "未知");
            throw new RuntimeException("SQL解析失败，无法应用数据权限过滤，为防止数据越权已拦截请求", e);
        }
    }
}
