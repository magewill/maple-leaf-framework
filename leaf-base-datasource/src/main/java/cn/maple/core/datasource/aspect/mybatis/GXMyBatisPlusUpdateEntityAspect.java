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
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
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
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusUpdateEntityAspect {
    private static final ConcurrentHashMap<String, List<ConditionToken>> WHERE_SQL_CONDITION_CACHE = new ConcurrentHashMap<>(128);

    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* update(..))")
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
        if (ObjectUtil.isEmpty(args) || args.length < 2 || ObjectUtil.isNull(args[0])) {
            return Dict.create();
        }

        Object entity = args[0];
        Object objectWrapper = args[1];
        Dict updateCondition = handleUpdateWrapper(objectWrapper);
        Dict entityData = Convert.convert(Dict.class, entity);

        return Dict.create()
                .set("entityData", entityData)
                .set("keyOperatorPairs", updateCondition.get("keyOperatorPairs"))
                .set("keyValuePairs", updateCondition.get("keyValuePairs"));
    }

    private void publishEvent(ProceedingJoinPoint point) {
        if (ObjectUtil.isNull(point) || ObjectUtil.isNull(point.getTarget())) {
            return;
        }

        try {
            Type[] mapperTypes = AopUtils.getTargetClass(point.getTarget()).getInterfaces();
            Method invokedMethod = ((MethodSignature) point.getSignature()).getMethod();
            if (ObjectUtil.isEmpty(mapperTypes)) {
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

                Dict source = handlePointArgs(point);
                if (ObjectUtil.isEmpty(source)) {
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
                GXEventPublisherUtils.publishEventAfterCommit(event);
                return;
            }
        } catch (Exception e) {
            log.error("Failed to publish update entity event", e);
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
        UpdateWrapper<T> updateWrapper = Convert.convert(new TypeReference<UpdateWrapper<T>>() {
        }, objectWrapper);
        return parseWhereSQL(updateWrapper);
    }

    private <T> Dict parseWhereSQL(UpdateWrapper<T> updateWrapper) {
        if (ObjectUtil.isNull(updateWrapper)) {
            return Dict.create().set("keyOperatorPairs", Dict.create()).set("keyValuePairs", Dict.create());
        }
        String whereSQL = updateWrapper.getTargetSql();
        Dict keyValuePairs = Dict.create();
        Dict keyOperatorPairs = Dict.create();
        Map<String, Object> paramNameValuePairs = updateWrapper.getParamNameValuePairs();
        if (CharSequenceUtil.isBlank(whereSQL) || ObjectUtil.isEmpty(paramNameValuePairs)) {
            return Dict.create().set("keyOperatorPairs", keyOperatorPairs).set("keyValuePairs", keyValuePairs);
        }

        List<ConditionToken> conditionTokens = WHERE_SQL_CONDITION_CACHE.computeIfAbsent(whereSQL, this::extractConditionTokens);
        for (int i = 0; i < conditionTokens.size(); i++) {
            ConditionToken token = conditionTokens.get(i);
            String paramName = Constants.WRAPPER_PARAM + (i + 1);
            keyOperatorPairs.set(token.field(), token.operator());
            keyValuePairs.set(token.field(), paramNameValuePairs.get(paramName));
        }

        return Dict.create().set("keyOperatorPairs", keyOperatorPairs).set("keyValuePairs", keyValuePairs);
    }

    private List<ConditionToken> extractConditionTokens(String whereSQL) {
        List<ConditionToken> conditionTokens = new ArrayList<>(8);
        try {
            Expression expression = CCJSqlParserUtil.parseCondExpression(whereSQL);
            expression.accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(AndExpression andExpression) {
                    andExpression.getLeftExpression().accept(this);
                    andExpression.getRightExpression().accept(this);
                }

                @Override
                public void visit(OrExpression orExpression) {
                    orExpression.getLeftExpression().accept(this);
                    orExpression.getRightExpression().accept(this);
                }

                @Override
                public void visit(EqualsTo equalsTo) {
                    appendToken(equalsTo.getLeftExpression().toString(), equalsTo.getStringExpression());
                }

                @Override
                public void visit(GreaterThanEquals greaterThanEquals) {
                    appendToken(greaterThanEquals.getLeftExpression().toString(), greaterThanEquals.getStringExpression());
                }

                @Override
                public void visit(LikeExpression likeExpression) {
                    appendToken(likeExpression.getLeftExpression().toString(), likeExpression.getStringExpression());
                }

                @Override
                public void visit(MinorThanEquals minorThanEquals) {
                    appendToken(minorThanEquals.getLeftExpression().toString(), minorThanEquals.getStringExpression());
                }

                private void appendToken(String leftExpression, String operator) {
                    String field = CharSequenceUtil.toCamelCase(leftExpression);
                    conditionTokens.add(new ConditionToken(field, operator));
                }
            });
        } catch (Exception e) {
            throw new GXBusinessException(e.getMessage(), e);
        }
        return List.copyOf(conditionTokens);
    }

    private Class<?> convertTypeToClass(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }

    private record ConditionToken(String field, String operator) {
    }
}

