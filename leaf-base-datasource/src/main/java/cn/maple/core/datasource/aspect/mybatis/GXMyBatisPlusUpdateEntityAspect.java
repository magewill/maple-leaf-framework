package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerAspectSupport;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerPayloadUtils;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
@Slf4j
public class GXMyBatisPlusUpdateEntityAspect {
    private static final Cache<String, ConditionParseResult> WHERE_SQL_CONDITION_CACHE = Caffeine.newBuilder()
            .maximumSize(2048)
            .expireAfterAccess(1, TimeUnit.DAYS)
            .build();
    private static final Pattern WHERE_PARAM_NAME_PATTERN = Pattern.compile(
            "#\\{[^}]*?\\.(" + Constants.WRAPPER_PARAM + "\\d+)(?:[,}])"
    );

    private final GXMyBatisListenerEventPublisher eventPublisher;

    public GXMyBatisPlusUpdateEntityAspect(GXMyBatisListenerEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("""
            target(cn.maple.core.datasource.mapper.GXBaseMapper)
            && (
                execution(int com.baomidou.mybatisplus.core.mapper.BaseMapper.update(
                    Object, com.baomidou.mybatisplus.core.conditions.Wrapper
                ))
                || execution(int com.baomidou.mybatisplus.core.mapper.BaseMapper.updateById(Object))
            )
            """)
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object proceed = point.proceed();
        if (!GXMyBatisListenerAspectSupport.isSuccessfulResult(proceed)) {
            return proceed;
        }
        GXMyBatisListenerAspectSupport.publishIfDeclared(eventPublisher, point,
                GXModelEventNamingEnums.SYNC_UPDATE_ENTITY, GXModelEventNamingEnums.ASYNC_UPDATE_ENTITY,
                () -> handlePointArgs(point));
        return proceed;
    }

    private Dict handlePointArgs(ProceedingJoinPoint point) {
        Object[] args = point.getArgs();
        if (ObjectUtil.isEmpty(args)) {
            return Dict.create();
        }

        Object entity = args[0];
        String operation = ((MethodSignature) point.getSignature()).getName();
        if (CharSequenceUtil.equals(operation, "updateById")) {
            if (ObjectUtil.isNull(entity)) {
                return Dict.create();
            }
            return Dict.create()
                    .set(GXMyBatisEventConstant.OPERATION, operation)
                    .set(GXMyBatisEventConstant.ENTITY_DATA, Convert.convert(Dict.class, entity))
                    .set(GXMyBatisEventConstant.KEY_OPERATOR_PAIRS, Dict.create())
                    .set(GXMyBatisEventConstant.KEY_VALUE_PAIRS, Dict.create())
                    .set(GXMyBatisEventConstant.CONDITION_LOSSLESS, Boolean.TRUE)
                    .set(GXMyBatisEventConstant.DEGRADE_REASONS, List.of())
                    // The primary-key condition is carried by entityData, so the raw condition is
                    // intentionally empty rather than absent (contracts/event-payload.md 空值语义).
                    .set(GXMyBatisEventConstant.RAW_WHERE_SQL, "")
                    .set(GXMyBatisEventConstant.RAW_CONDITION_SQL, "");
        }
        if (args.length < 2) {
            return Dict.create();
        }
        Dict updateCondition = handleUpdateWrapper(args[1]);
        Dict entityData = ObjectUtil.isNull(entity) ? Dict.create() : Convert.convert(Dict.class, entity);

        return Dict.create()
                .set(GXMyBatisEventConstant.OPERATION, operation)
                .set(GXMyBatisEventConstant.ENTITY_DATA, entityData)
                .set(GXMyBatisEventConstant.KEY_OPERATOR_PAIRS,
                        updateCondition.get(GXMyBatisEventConstant.KEY_OPERATOR_PAIRS))
                .set(GXMyBatisEventConstant.KEY_VALUE_PAIRS,
                        updateCondition.get(GXMyBatisEventConstant.KEY_VALUE_PAIRS))
                .set(GXMyBatisEventConstant.RAW_WHERE_SQL, updateCondition.getStr(GXMyBatisEventConstant.RAW_WHERE_SQL))
                .set(GXMyBatisEventConstant.RAW_CONDITION_SQL,
                        updateCondition.getStr(GXMyBatisEventConstant.RAW_CONDITION_SQL))
                .set(GXMyBatisEventConstant.CONDITION_LOSSLESS,
                        Convert.toBool(updateCondition.get(GXMyBatisEventConstant.CONDITION_LOSSLESS), Boolean.FALSE))
                .set(GXMyBatisEventConstant.DEGRADE_REASONS,
                        updateCondition.get(GXMyBatisEventConstant.DEGRADE_REASONS))
                .set(GXMyBatisEventConstant.SNAPSHOT_DEGRADED,
                        Convert.toBool(updateCondition.get(GXMyBatisEventConstant.SNAPSHOT_DEGRADED), Boolean.FALSE));
    }

    private Dict handleUpdateWrapper(Object objectWrapper) {
        if (objectWrapper instanceof AbstractWrapper<?, ?, ?> updateWrapper) {
            return parseWhereSQL(updateWrapper);
        }
        String rawSql = objectWrapper instanceof Wrapper<?> wrapper ? wrapper.getTargetSql() : "";
        return degradedConditionPayload(rawSql, List.of(GXMyBatisListenerDegradeReasonEnums.NON_ABSTRACT_WRAPPER));
    }

    /**
     * Parses the wrapper condition into structured fields, falling back to the raw SQL whenever the
     * structured form would not be faithful. {@code conditionLossless} stays {@code false} for every
     * fallback, so a lost condition is always visible to listeners and to the degrade counter
     * (FR-009/FR-010).
     */
    private Dict parseWhereSQL(AbstractWrapper<?, ?, ?> updateWrapper) {
        String whereSQL = updateWrapper.getTargetSql();
        if (CharSequenceUtil.isBlank(whereSQL)) {
            // A wrapper without any condition (for example an UpdateWrapper that only carries
            // set(...) clauses) has nothing to restore, so the empty condition set is lossless.
            // Claiming a degrade here would contradict data-model §3 (a degraded payload must carry
            // the original condition text) and would inflate `degraded` for a faithful payload.
            return losslessConditionPayload(Dict.create(), Dict.create(), "", List.of());
        }

        ConditionParseResult conditionParseResult = WHERE_SQL_CONDITION_CACHE.get(whereSQL, this::extractConditionTokens);
        if (ObjectUtil.isNull(conditionParseResult)) {
            return degradedConditionPayload(whereSQL, List.of(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR));
        }
        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons = new ArrayList<>(conditionParseResult.degradeReasons());
        boolean tokensUsable = conditionParseResult.lossless() && !ObjectUtil.isEmpty(conditionParseResult.conditionTokens());
        if (!tokensUsable) {
            return degradedConditionPayload(whereSQL, degradeReasons);
        }

        Map<String, Object> paramNameValuePairs = updateWrapper.getParamNameValuePairs();
        List<String> whereParamNames = extractWhereParamNames(updateWrapper.getSqlSegment());
        if (ObjectUtil.isNull(paramNameValuePairs)
                || !hasMatchingWhereParameters(conditionParseResult.conditionTokens(), whereParamNames, paramNameValuePairs)) {
            degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.PARAM_MISMATCH);
            return degradedConditionPayload(whereSQL, degradeReasons);
        }

        Dict keyOperatorPairs = Dict.create();
        Dict keyValuePairs = Dict.create();
        boolean snapshotDegraded = false;
        int paramNameIndex = 0;
        for (ConditionToken token : conditionParseResult.conditionTokens()) {
            keyOperatorPairs.set(token.field(), token.operator());
            if (token.paramCount() <= 0) {
                keyValuePairs.set(token.field(), null);
                continue;
            }
            List<Object> values = new ArrayList<>(token.paramCount());
            for (int index = 0; index < token.paramCount(); index++) {
                Object rawValue = paramNameValuePairs.get(whereParamNames.get(paramNameIndex++));
                snapshotDegraded |= GXMyBatisListenerPayloadUtils.isUnsnapshotable(rawValue);
                values.add(snapshotValue(rawValue));
            }
            keyValuePairs.set(token.field(), token.paramCount() == 1 ? values.get(0) : values);
        }
        if (snapshotDegraded) {
            degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE);
        }
        return losslessConditionPayload(keyOperatorPairs, keyValuePairs, whereSQL, degradeReasons);
    }

    /**
     * A degraded payload keeps the raw SQL because the structured form could not represent it, so
     * {@code conditionLossless} is {@code false} and the reasons stay visible (FR-009/FR-010).
     */
    private Dict degradedConditionPayload(String rawSql, List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
        return conditionPayload(Dict.create(), Dict.create(), rawSql, false, degradeReasons);
    }

    /**
     * A lossless payload represents the whole condition structurally.
     */
    private Dict losslessConditionPayload(Dict keyOperatorPairs, Dict keyValuePairs, String rawSql,
                                          List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
        return conditionPayload(keyOperatorPairs, keyValuePairs, rawSql, true, degradeReasons);
    }

    /**
     * Both payload variants differ only in whether the structured form is faithful, so they are built
     * here once. {@code snapshotDegraded} is derived from the recorded reason instead of a second flag,
     * so the marker and the reason cannot disagree.
     */
    private Dict conditionPayload(Dict keyOperatorPairs, Dict keyValuePairs, String rawSql, boolean lossless,
                                  List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
        return Dict.create()
                .set(GXMyBatisEventConstant.KEY_OPERATOR_PAIRS, keyOperatorPairs)
                .set(GXMyBatisEventConstant.KEY_VALUE_PAIRS, keyValuePairs)
                .set(GXMyBatisEventConstant.RAW_WHERE_SQL, rawSql)
                .set(GXMyBatisEventConstant.RAW_CONDITION_SQL, rawSql)
                .set(GXMyBatisEventConstant.CONDITION_LOSSLESS, lossless)
                .set(GXMyBatisEventConstant.DEGRADE_REASONS, List.copyOf(degradeReasons))
                .set(GXMyBatisEventConstant.SNAPSHOT_DEGRADED,
                        lossless && degradeReasons.contains(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE));
    }

    /**
     * Copies a condition value so listeners cannot reach caller-owned mutable state; a value that
     * cannot be copied is kept and reported through
     * {@link GXMyBatisListenerPayloadUtils#isUnsnapshotable(Object)}.
     */
    private static @Nullable Object snapshotValue(@Nullable Object rawValue) {
        try {
            return ObjectUtil.cloneIfPossible(rawValue);
        } catch (RuntimeException exception) {
            return rawValue;
        }
    }

    private List<String> extractWhereParamNames(String whereSqlSegment) {
        List<String> paramNames = new ArrayList<>();
        if (CharSequenceUtil.isBlank(whereSqlSegment)) {
            return paramNames;
        }
        Matcher matcher = WHERE_PARAM_NAME_PATTERN.matcher(whereSqlSegment);
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }
        return paramNames;
    }

    private boolean hasMatchingWhereParameters(List<ConditionToken> conditionTokens, List<String> whereParamNames,
                                               Map<String, Object> paramNameValuePairs) {
        int expectedParamCount = conditionTokens.stream().mapToInt(ConditionToken::paramCount).sum();
        return expectedParamCount == whereParamNames.size()
                && whereParamNames.stream().allMatch(paramNameValuePairs::containsKey);
    }

    private ConditionParseResult extractConditionTokens(String whereSQL) {
        if (CharSequenceUtil.isBlank(whereSQL)) {
            return new ConditionParseResult(List.of(), false, List.of());
        }
        ConditionTokenVisitor visitor = new ConditionTokenVisitor();
        try {
            CCJSqlParserUtil.parseCondExpression(whereSQL).accept(visitor, null);
        } catch (Exception exception) {
            log.warn("Failed to parse update wrapper conditions, fallback to raw sql only: {}", whereSQL, exception);
            visitor.markLossy(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR);
        }
        return visitor.toResult();
    }

    private record ConditionToken(String field, String operator, int paramCount) {
    }

    private record ConditionParseResult(List<ConditionToken> conditionTokens, boolean lossless,
                                        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
    }

    /**
     * Collects one {@link ConditionToken} per {@code AND}-combined condition and records why the
     * structured form cannot represent the condition faithfully: {@code OR} nesting, a computed
     * left-hand side or a repeated field all make the payload lossy (FR-009/FR-010).
     */
    private static final class ConditionTokenVisitor extends ExpressionVisitorAdapter<Void> {
        private final List<ConditionToken> conditionTokens = new ArrayList<>(8);
        private final List<GXMyBatisListenerDegradeReasonEnums> degradeReasons = new ArrayList<>(2);
        private boolean lossless = true;

        @Override
        public <S> Void visit(AndExpression andExpression, S context) {
            andExpression.getLeftExpression().accept(this, context);
            andExpression.getRightExpression().accept(this, context);
            return null;
        }

        @Override
        public <S> Void visit(OrExpression orExpression, S context) {
            markLossy(GXMyBatisListenerDegradeReasonEnums.OR_EXPRESSION);
            orExpression.getLeftExpression().accept(this, context);
            orExpression.getRightExpression().accept(this, context);
            return null;
        }

        @Override
        public <S> Void visit(Between between, S context) {
            appendToken(between.getLeftExpression(), between.isNot() ? "not between" : "between", 2);
            return null;
        }

        @Override
        public <S> Void visit(InExpression inExpression, S context) {
            appendToken(inExpression.getLeftExpression(), inExpression.isNot() ? "not in" : "in",
                    countExpressionValues(inExpression.getRightExpression()));
            return null;
        }

        @Override
        public <S> Void visit(IsNullExpression isNullExpression, S context) {
            appendToken(isNullExpression.getLeftExpression(), isNullExpression.isNot() ? "is not null" : "is null", 0);
            return null;
        }

        @Override
        protected <S> Void visitBinaryExpression(BinaryExpression binaryExpression, S context) {
            appendToken(binaryExpression.getLeftExpression(), binaryExpression.getStringExpression(), 1);
            return null;
        }

        @Override
        public <S> Void visit(ExpressionList<? extends Expression> expressionList, S context) {
            if (ObjectUtil.isNull(expressionList) || expressionList.isEmpty()) {
                return null;
            }
            for (Expression item : expressionList) {
                if (ObjectUtil.isNotNull(item)) {
                    item.accept(this, context);
                }
            }
            return null;
        }

        private void appendToken(Expression leftExpression, String operator, int paramCount) {
            if (!(leftExpression instanceof Column column)) {
                markLossy(GXMyBatisListenerDegradeReasonEnums.NON_COLUMN_LEFT);
                return;
            }
            String field = CharSequenceUtil.toCamelCase(column.getColumnName());
            if (conditionTokens.stream().anyMatch(token -> CharSequenceUtil.equals(token.field(), field))) {
                markLossy(GXMyBatisListenerDegradeReasonEnums.DUPLICATE_FIELD);
            }
            conditionTokens.add(new ConditionToken(field, operator, Math.max(0, paramCount)));
        }

        private void markLossy(GXMyBatisListenerDegradeReasonEnums reason) {
            lossless = false;
            if (!degradeReasons.contains(reason)) {
                degradeReasons.add(reason);
            }
        }

        private ConditionParseResult toResult() {
            return new ConditionParseResult(List.copyOf(conditionTokens), lossless, List.copyOf(degradeReasons));
        }

        private int countExpressionValues(Expression expression) {
            if (ObjectUtil.isNull(expression)) {
                return 0;
            }
            if (expression instanceof ExpressionList<?> expressionList) {
                return expressionList.size();
            }
            return 1;
        }
    }
}
