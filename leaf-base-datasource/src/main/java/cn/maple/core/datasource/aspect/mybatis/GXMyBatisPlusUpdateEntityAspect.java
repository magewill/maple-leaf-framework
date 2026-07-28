package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateEntityEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusUpdateEntityAspect {
    private static final Cache<String, ConditionParseResult> WHERE_SQL_CONDITION_CACHE = Caffeine.newBuilder()
            .maximumSize(2048)
            .expireAfterAccess(1, TimeUnit.DAYS)
            .build();

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
        if (!isSuccessfulResult(proceed)) {
            return proceed;
        }
        publishEvent(point);
        return proceed;
    }

    private boolean isSuccessfulResult(Object result) {
        if (ObjectUtil.isNull(result)) {
            return false;
        }
        if (result instanceof Boolean boolResult) {
            return boolResult;
        }
        if (result instanceof Number numResult) {
            return numResult.longValue() > 0L;
        }
        return true;
    }

    private Dict handlePointArgs(ProceedingJoinPoint point) {
        Object[] args = point.getArgs();
        if (ObjectUtil.isEmpty(args) || ObjectUtil.isNull(args[0])) {
            return Dict.create();
        }

        Object entity = args[0];
        String operation = ((MethodSignature) point.getSignature()).getName();
        if (CharSequenceUtil.equals(operation, "updateById")) {
            return Dict.create()
                    .set("operation", operation)
                    .set("entityData", Convert.convert(Dict.class, entity))
                    .set("keyOperatorPairs", Dict.create())
                    .set("keyValuePairs", Dict.create());
        }
        if (args.length < 2) {
            return Dict.create();
        }
        Object objectWrapper = args[1];
        Dict updateCondition = handleUpdateWrapper(objectWrapper);
        Dict entityData = Convert.convert(Dict.class, entity);

        return Dict.create()
                .set("operation", operation)
                .set("entityData", entityData)
                .set("keyOperatorPairs", updateCondition.get("keyOperatorPairs"))
                .set("keyValuePairs", updateCondition.get("keyValuePairs"));
    }

    private void publishEvent(ProceedingJoinPoint point) {
        if (ObjectUtil.isNull(point) || ObjectUtil.isNull(point.getTarget())) {
            return;
        }

        Type[] mapperTypes = AopUtils.getTargetClass(point.getTarget()).getInterfaces();
        Method invokedMethod = ((MethodSignature) point.getSignature()).getMethod();
        if (ObjectUtil.isEmpty(mapperTypes)) {
            return;
        }

        Dict source = handlePointArgs(point);
        if (ObjectUtil.isEmpty(source)) {
            return;
        }

        for (Type type : mapperTypes) {
            Class<?> mapperClass = convertTypeToClass(type);
            if (ObjectUtil.isNull(mapperClass)) {
                continue;
            }

            GXMyBatisListener listenerConfig = resolveListenerConfig(mapperClass, invokedMethod);
            if (ObjectUtil.isNull(listenerConfig)) {
                continue;
            }

            Class<? extends GXMybatisListenerService> listenerClass = listenerConfig.listenerClazz();
            String eventType = GXModelEventNamingEnums.SYNC_UPDATE_ENTITY.getEventType();
            String eventName = GXModelEventNamingEnums.SYNC_UPDATE_ENTITY.getEventName();
            if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                eventType = GXModelEventNamingEnums.ASYNC_UPDATE_ENTITY.getEventType();
                eventName = GXModelEventNamingEnums.ASYNC_UPDATE_ENTITY.getEventName();
            }

            Dict eventParam = Dict.create()
                    .set("listenerClazzName", listenerClass.getSimpleName())
                    .set("listenerClazz", listenerClass);
            GXMyBatisModelUpdateEntityEvent<Dict> event = new GXMyBatisModelUpdateEntityEvent<>(source, eventType, eventParam, eventName);
            if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                GXEventPublisherUtils.publishEventAfterCommit(event);
            } else {
                GXEventPublisherUtils.publishEvent(event);
            }
        }
    }

    private GXMyBatisListener resolveListenerConfig(Class<?> mapperClass, Method invokedMethod) {
        Method mapperMethod = findMethod(mapperClass, invokedMethod);
        if (ObjectUtil.isNotNull(mapperMethod)) {
            GXMyBatisListener methodAnno = AnnotationUtil.getAnnotation(mapperMethod, GXMyBatisListener.class);
            if (ObjectUtil.isNotNull(methodAnno)) {
                return methodAnno;
            }
        }
        return AnnotationUtil.getAnnotation(mapperClass, GXMyBatisListener.class);
    }

    private Method findMethod(Class<?> mapperClass, Method invokedMethod) {
        try {
            return mapperClass.getMethod(invokedMethod.getName(), invokedMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private <T> Dict handleUpdateWrapper(Object objectWrapper) {
        UpdateWrapper<T> updateWrapper = Convert.convert(new TypeReference<>() {
        }, objectWrapper);
        return parseWhereSQL(updateWrapper);
    }

    private <T> Dict parseWhereSQL(UpdateWrapper<T> updateWrapper) {
        Dict keyValuePairs = Dict.create();
        Dict keyOperatorPairs = Dict.create();
        Dict result = Dict.create()
                .set("keyOperatorPairs", keyOperatorPairs)
                .set("keyValuePairs", keyValuePairs);
        if (ObjectUtil.isNull(updateWrapper)) {
            return result;
        }

        String whereSQL = updateWrapper.getTargetSql();
        Map<String, Object> paramNameValuePairs = updateWrapper.getParamNameValuePairs();
        if (CharSequenceUtil.isBlank(whereSQL)) {
            return result.set("rawWhereSql", whereSQL);
        }

        ConditionParseResult conditionParseResult = WHERE_SQL_CONDITION_CACHE.get(whereSQL, this::extractConditionTokens);
        if (ObjectUtil.isEmpty(paramNameValuePairs)
                || ObjectUtil.isNull(conditionParseResult)
                || !conditionParseResult.lossless()
                || ObjectUtil.isEmpty(conditionParseResult.conditionTokens())) {
            return result.set("rawWhereSql", whereSQL);
        }

        int paramIndex = 1;
        for (ConditionToken token : conditionParseResult.conditionTokens()) {
            keyOperatorPairs.set(token.field(), token.operator());
            if (token.paramCount() <= 0) {
                keyValuePairs.set(token.field(), null);
                continue;
            }
            if (token.paramCount() == 1) {
                String paramName = Constants.WRAPPER_PARAM + paramIndex++;
                keyValuePairs.set(token.field(), paramNameValuePairs.get(paramName));
                continue;
            }
            List<Object> values = new ArrayList<>(token.paramCount());
            for (int i = 0; i < token.paramCount(); i++) {
                String paramName = Constants.WRAPPER_PARAM + paramIndex++;
                values.add(paramNameValuePairs.get(paramName));
            }
            keyValuePairs.set(token.field(), values);
        }

        return result.set("rawWhereSql", whereSQL);
    }

    private ConditionParseResult extractConditionTokens(String whereSQL) {
        List<ConditionToken> conditionTokens = new ArrayList<>(8);
        if (CharSequenceUtil.isBlank(whereSQL)) {
            return new ConditionParseResult(List.copyOf(conditionTokens), false);
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
                    orExpression.getLeftExpression().accept(this, context);
                    orExpression.getRightExpression().accept(this, context);
                    return null;
                }

                @Override
                public <S> Void visit(Between between, S context) {
                    appendToken(between.getLeftExpression().toString(), between.isNot() ? "not between" : "between", 2);
                    return null;
                }

                @Override
                public <S> Void visit(InExpression inExpression, S context) {
                    appendToken(inExpression.getLeftExpression().toString(), inExpression.isNot() ? "not in" : "in", countExpressionValues(inExpression.getRightExpression()));
                    return null;
                }

                @Override
                public <S> Void visit(IsNullExpression isNullExpression, S context) {
                    appendToken(isNullExpression.getLeftExpression().toString(), isNullExpression.isNot() ? "is not null" : "is null", 0);
                    return null;
                }

                @Override
                protected <S> Void visitBinaryExpression(BinaryExpression binaryExpression, S context) {
                    appendToken(binaryExpression.getLeftExpression().toString(), binaryExpression.getStringExpression(), 1);
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

                private void appendToken(String leftExpression, String operator, int paramCount) {
                    String field = CharSequenceUtil.toCamelCase(leftExpression);
                    if (conditionTokens.stream().anyMatch(token -> CharSequenceUtil.equals(token.field(), field))) {
                        lossless[0] = false;
                    }
                    conditionTokens.add(new ConditionToken(field, operator, Math.max(0, paramCount)));
                }
            }, null);
        } catch (Exception e) {
            log.warn("Failed to parse update wrapper conditions, fallback to raw sql only: {}", whereSQL, e);
            lossless[0] = false;
        }

        return new ConditionParseResult(List.copyOf(conditionTokens), lossless[0]);
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

    private Class<?> convertTypeToClass(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }

    private record ConditionToken(String field, String operator, int paramCount) {
    }

    private record ConditionParseResult(List<ConditionToken> conditionTokens, boolean lossless) {
    }
}
