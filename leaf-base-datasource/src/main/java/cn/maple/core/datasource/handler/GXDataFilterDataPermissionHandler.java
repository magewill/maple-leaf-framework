package cn.maple.core.datasource.handler;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.dto.GXDataFilterContext;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

@Slf4j
public class GXDataFilterDataPermissionHandler implements DataPermissionHandler {
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        GXDataFilterContext context = GXDataFilterThreadLocalUtils.getDataFilterContext();
        if (context == null || CharSequenceUtil.isBlank(context.getSqlFilter())) {
            return where;
        }

        try {
            Expression filterExpression = CCJSqlParserUtil.parseCondExpression(context.getSqlFilter());
            if (where == null) {
                return new ParenthesedExpressionList<>(filterExpression);
            }
            return new AndExpression(new ParenthesedExpressionList<>(where), new ParenthesedExpressionList<>(filterExpression));
        } catch (JSQLParserException e) {
            log.error("SQL解析失败，无法应用数据权限过滤: {}, 原因: {}", e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "未知");
            throw new RuntimeException("SQL解析失败，无法应用数据权限过滤，为防止数据越权已拦截请求", e);
        }
    }
}
