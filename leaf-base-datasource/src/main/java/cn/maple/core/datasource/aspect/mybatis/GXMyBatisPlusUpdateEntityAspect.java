package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerAspectSupport;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
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
import org.springframework.stereotype.Component;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusUpdateEntityAspect {
    private static final Cache<String, ConditionParseResult> WHERE_SQL_CONDITION_CACHE = Caffeine.newBuilder()
            .maximumSize(2048)
            .expireAfterAccess(1, TimeUnit.DAYS)
            .build();
    private static final Pattern WHERE_PARAM_NAME_PATTERN = Pattern.compile(
            "#\\{[^}]*?\\.(" + Constants.WRAPPER_PARAM + "\\d+)(?:[,}])"
    );
    private static final String OPERATION = "operation";
    private static final String ENTITY_DATA = "entityData";
    private static final String KEY_OPERATOR_PAIRS = "keyOperatorPairs";
    private static final String KEY_VALUE_PAIRS = "keyValuePairs";
    private static final String RAW_WHERE_SQL = "rawWhereSql";
    private static final String RAW_CONDITION_SQL = "rawConditionSql";
    private static final String CONDITION_LOSSLESS = "conditionLossless";
    private static final String DEGRADE_REASONS = "degradeReasons";
    private static final String SNAPSHOT_DEGRADED = "snapshotDegraded";

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
                    .set(OPERATION, operation)
                    .set(ENTITY_DATA, Convert.convert(Dict.class, entity))
                    .set(KEY_OPERATOR_PAIRS, Dict.create())
                    .set(KEY_VALUE_PAIRS, Dict.create())
                    .set(CONDITION_LOSSLESS, Boolean.TRUE)
                    .set(DEGRADE_REASONS, List.of())
                    // The primary-key condition is carried by entityData, so the raw condition is
                    // intentionally empty rather than absent (contracts/event-payload.md 空值语义).
                    .set(RAW_WHERE_SQL, "")
                    .set(RAW_CONDITION_SQL, "");
        }
        if (args.length < 2) {
            return Dict.create();
        }
        Object objectWrapper = args[1];
        Dict updateCondition = handleUpdateWrapper(objectWrapper);
        Dict entityData = ObjectUtil.isNull(entity) ? Dict.create() : Convert.convert(Dict.class, entity);

        return Dict.create()
                .set(OPERATION, operation)
                .set(ENTITY_DATA, entityData)
                .set(KEY_OPERATOR_PAIRS, updateCondition.get(KEY_OPERATOR_PAIRS))
                .set(KEY_VALUE_PAIRS, updateCondition.get(KEY_VALUE_PAIRS))
                .set(RAW_WHERE_SQL, updateCondition.getStr(RAW_WHERE_SQL))
                .set(RAW_CONDITION_SQL, updateCondition.getStr(RAW_CONDITION_SQL))
                .set(CONDITION_LOSSLESS, Convert.toBool(updateCondition.get(CONDITION_LOSSLESS), Boolean.FALSE))
                .set(DEGRADE_REASONS, updateCondition.get(DEGRADE_REASONS))
                .set(SNAPSHOT_DEGRADED, Convert.toBool(updateCondition.get(SNAPSHOT_DEGRADED), Boolean.FALSE));
    }

    private <T> Dict handleUpdateWrapper(Object objectWrapper) {
        if (objectWrapper instanceof AbstractWrapper<?, ?, ?> updateWrapper) {
            return parseWhereSQL(updateWrapper);
        }
        if (objectWrapper instanceof Wrapper<?> wrapper) {
            return degradedPayload(wrapper.getTargetSql(), GXMyBatisListenerDegradeReasonEnums.NON_ABSTRACT_WRAPPER);
        }
        return degradedPayload("", GXMyBatisListenerDegradeReasonEnums.NON_ABSTRACT_WRAPPER);
    }

    private Dict degradedPayload(String rawSql, GXMyBatisListenerDegradeReasonEnums reason) {
        return Dict.create()
                .set(KEY_OPERATOR_PAIRS, Dict.create())
                .set(KEY_VALUE_PAIRS, Dict.create())
                .set(RAW_WHERE_SQL, rawSql)
                .set(RAW_CONDITION_SQL, rawSql)
                .set(CONDITION_LOSSLESS, Boolean.FALSE)
                .set(DEGRADE_REASONS, List.of(reason))
                .set(SNAPSHOT_DEGRADED, Boolean.FALSE);
    }

    private Dict parseWhereSQL(AbstractWrapper<?, ?, ?> updateWrapper) {
        Dict keyValuePairs = Dict.create();
        Dict keyOperatorPairs = Dict.create();
        Dict result = Dict.create()
                .set(KEY_OPERATOR_PAIRS, keyOperatorPairs)
                .set(KEY_VALUE_PAIRS, keyValuePairs);
        if (ObjectUtil.isNull(updateWrapper)) {
            return degradedPayload("", GXMyBatisListenerDegradeReasonEnums.NON_ABSTRACT_WRAPPER);
        }

        String whereSQL = updateWrapper.getTargetSql();
        Map<String, Object> paramNameValuePairs = updateWrapper.getParamNameValuePairs();
        if (CharSequenceUtil.isBlank(whereSQL)) {
            return result
                    .set(RAW_WHERE_SQL, whereSQL)
                    .set(RAW_CONDITION_SQL, whereSQL)
                    .set(CONDITION_LOSSLESS, Boolean.FALSE)
                    .set(DEGRADE_REASONS, List.of())
                    .set(SNAPSHOT_DEGRADED, Boolean.FALSE);
        }

        ConditionParseResult conditionParseResult = WHERE_SQL_CONDITION_CACHE.get(whereSQL, this::extractConditionTokens);
        if (ObjectUtil.isNull(conditionParseResult)) {
            return degradedPayload(whereSQL, GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR);
        }
        List<String> whereParamNames = extractWhereParamNames(updateWrapper.getSqlSegment());
        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons = new ArrayList<>(conditionParseResult.degradeReasons());
        if (ObjectUtil.isNull(paramNameValuePairs)
                || !conditionParseResult.lossless()
                || ObjectUtil.isEmpty(conditionParseResult.conditionTokens())
                || !hasMatchingWhereParameters(conditionParseResult.conditionTokens(), whereParamNames, paramNameValuePairs)) {
            if (!conditionParseResult.lossless() || ObjectUtil.isEmpty(conditionParseResult.conditionTokens())) {
                return result
                        .set(RAW_WHERE_SQL, whereSQL)
                        .set(RAW_CONDITION_SQL, whereSQL)
                        .set(CONDITION_LOSSLESS, Boolean.FALSE)
                        .set(DEGRADE_REASONS, List.copyOf(degradeReasons))
                        .set(SNAPSHOT_DEGRADED, Boolean.FALSE);
            }
            degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.PARAM_MISMATCH);
            return result
                    .set(RAW_WHERE_SQL, whereSQL)
                    .set(RAW_CONDITION_SQL, whereSQL)
                    .set(CONDITION_LOSSLESS, Boolean.FALSE)
                    .set(DEGRADE_REASONS, List.copyOf(degradeReasons))
                    .set(SNAPSHOT_DEGRADED, Boolean.FALSE);
        }

        boolean snapshotDegraded = false;
        int paramNameIndex = 0;
        for (ConditionToken token : conditionParseResult.conditionTokens()) {
            keyOperatorPairs.set(token.field(), token.operator());
            if (token.paramCount() <= 0) {
                keyValuePairs.set(token.field(), null);
                continue;
            }
            if (token.paramCount() == 1) {
                String paramName = whereParamNames.get(paramNameIndex++);
                Object rawValue = paramNameValuePairs.get(paramName);
                snapshotDegraded |= isUnsnapshotable(rawValue);
                keyValuePairs.set(token.field(), snapshotValue(rawValue));
                continue;
            }
            List<Object> values = new ArrayList<>(token.paramCount());
            for (int i = 0; i < token.paramCount(); i++) {
                String paramName = whereParamNames.get(paramNameIndex++);
                Object rawValue = paramNameValuePairs.get(paramName);
                snapshotDegraded |= isUnsnapshotable(rawValue);
                values.add(snapshotValue(rawValue));
            }
            keyValuePairs.set(token.field(), values);
        }
        if (snapshotDegraded) {
            degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE);
        }

        return result
                .set(RAW_WHERE_SQL, whereSQL)
                .set(RAW_CONDITION_SQL, whereSQL)
                .set(CONDITION_LOSSLESS, Boolean.TRUE)
                .set(DEGRADE_REASONS, List.copyOf(degradeReasons))
                .set(SNAPSHOT_DEGRADED, snapshotDegraded);
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
        List<ConditionToken> conditionTokens = new ArrayList<>(8);
        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons = new ArrayList<>(2);
        if (CharSequenceUtil.isBlank(whereSQL)) {
            return new ConditionParseResult(List.copyOf(conditionTokens), false, List.copyOf(degradeReasons));
        }

        boolean[] lossless = {true};
        try {
            Expression expression = CCJSqlParserUtil.parseCondExpression(whereSQL);
            expression.accept(new ExpressionVisitorAdapter<Void>() {
                @Override
                public <S> Void visit(AndExpression andExpression, S context) {
                    andExpression.getLeftExpression().accept(this, context);
                    andExpression.getRightExpression().accept(this, context);
                    return null;
                }

                @Override
                public <S> Void visit(OrExpression orExpression, S context) {
                    lossless[0] = false;
                    degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.OR_EXPRESSION);
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
                    appendToken(inExpression.getLeftExpression(), inExpression.isNot() ? "not in" : "in", countExpressionValues(inExpression.getRightExpression()));
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
                    for (Expression item : expressionList.getExpressions()) {
                        if (ObjectUtil.isNotNull(item)) {
                            item.accept(this, context);
                        }
                    }
                    return null;
                }

                private void appendToken(Expression leftExpression, String operator, int paramCount) {
                    if (!(leftExpression instanceof Column column)) {
                        lossless[0] = false;
                        addReason(GXMyBatisListenerDegradeReasonEnums.NON_COLUMN_LEFT);
                        return;
                    }
                    String field = CharSequenceUtil.toCamelCase(column.getColumnName());
                    if (conditionTokens.stream().anyMatch(token -> CharSequenceUtil.equals(token.field(), field))) {
                        lossless[0] = false;
                        addReason(GXMyBatisListenerDegradeReasonEnums.DUPLICATE_FIELD);
                    }
                    conditionTokens.add(new ConditionToken(field, operator, Math.max(0, paramCount)));
                }

                private void addReason(GXMyBatisListenerDegradeReasonEnums reason) {
                    if (!degradeReasons.contains(reason)) {
                        degradeReasons.add(reason);
                    }
                }
            }, null);
        } catch (Exception e) {
            log.warn("Failed to parse update wrapper conditions, fallback to raw sql only: {}", whereSQL, e);
            lossless[0] = false;
            if (!degradeReasons.contains(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR)) {
                degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR);
            }
        }

        return new ConditionParseResult(List.copyOf(conditionTokens), lossless[0], List.copyOf(degradeReasons));
    }

    private int countExpressionValues(Expression expression) {
        if (ObjectUtil.isNull(expression)) {
            return 0;
        }
        if (expression instanceof ExpressionList<?> expressionList) {
            return expressionList.getExpressions().size();
        }
        return 1;
    }

    private boolean isUnsnapshotable(Object value) {
        if (ObjectUtil.isNull(value)) {
            return false;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof UUID
                || value instanceof Temporal) {
            return false;
        }
        try {
            return ObjectUtil.cloneIfPossible(value) == value;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private Object snapshotValue(Object value) {
        try {
            return ObjectUtil.cloneIfPossible(value);
        } catch (RuntimeException exception) {
            return value;
        }
    }

    private record ConditionToken(String field, String operator, int paramCount) {
    }

    private record ConditionParseResult(List<ConditionToken> conditionTokens, boolean lossless,
                                        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
    }
}
