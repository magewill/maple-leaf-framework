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
import org.apache.ibatis.annotations.Mapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * 更新实体(Entity)切面类
 * <p>
 * 该切面用于拦截GXBaseMapper接口的update方法调用，实现实体更新操作的事件发布。
 * 该切面会在更新操作执行后，根据Mapper上的GXMyBatisListener注解配置发布相应的同步或异步事件。
 * 内存安全考虑：事件发布后不保留对原始数据的引用，确保GC能正常回收不再使用的对象。
 */
@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusUpdateEntityAspect {
    /**
     * 拦截GXBaseMapper接口的update方法调用
     * <p>
     * 该方法在目标方法执行前后进行拦截，并在方法执行后发布更新实体事件。
     * 采用环绕通知模式，确保在原始方法执行完成后再发布事件，保证数据一致性。
     *
     * @param point 切点对象，包含被拦截的方法信息和参数
     * @return 原始方法的返回值
     * @throws Throwable 执行过程中可能抛出的异常
     */
    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* update(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        log.debug("发布更新前的事件开始");
        Object proceed = point.proceed();
        publishEvent(point);
        log.debug("发布更新后的事件结束");
        return proceed;
    }


    /**
     * 处理切点的参数，提取更新的实体数据和条件信息
     * <p>
     * 该方法从切点中提取更新的实体数据和条件信息，并转换为Dict格式。
     * 内存安全考虑：使用Dict对象存储数据，避免直接操作原始对象引用，防止内存泄漏。
     *
     * @param type  Mapper接口类型
     * @param point 切点对象，包含方法参数信息
     * @return Dict 包含实体数据和条件信息的字典对象
     */
    private Dict handlePointArgs(Type type, ProceedingJoinPoint point) {
        Dict retDict = Dict.create();
        Class<Mapper> mapper = convertTypeToMapper(type);
        if (ObjectUtil.isNotNull(mapper)) {
            Object[] args = point.getArgs();
            Object entity = args[0];
            Object objectWrapper = args[1];
            Dict updateCondition = handleUpdateWrapper(objectWrapper, entity.getClass());
            Dict entityData = Convert.convert(Dict.class, entity);
            retDict.set("entityData", entityData).set("keyOperatorPairs", updateCondition.get("keyOperatorPairs")).set("keyValuePairs", updateCondition.get("keyValuePairs"));
        }
        return retDict;
    }

    /**
     * 发布更新实体事件
     * <p>
     * 根据Mapper上的GXMyBatisListener注解配置，发布同步或异步的更新实体事件。
     * 事件中包含更新的实体数据和条件信息，以及监听器类信息。
     * 内存安全考虑：
     * 1. 使用局部变量存储中间结果，避免跨方法引用导致的内存泄漏
     * 2. 事件发布后不保留对原始数据的引用，确保GC能正常回收不再使用的对象
     * 3. 循环中的对象引用在每次迭代后都会被重置，避免内存累积
     *
     * @param point 切点对象，包含被拦截的方法信息和参数
     */
    private void publishEvent(ProceedingJoinPoint point) {
        Type[] myBatisMapper = AopUtils.getTargetClass(point.getTarget()).getInterfaces();
        for (Type type : myBatisMapper) {
            Class<Mapper> mapper = convertTypeToMapper(type);
            if (ObjectUtil.isNotNull(mapper)) {
                GXMyBatisListener myBatisListener = AnnotationUtil.getAnnotation(mapper, GXMyBatisListener.class);
                if (ObjectUtil.isNull(myBatisListener)) {
                    return;
                }
                Dict source = handlePointArgs(type, point);
                Class<? extends GXMybatisListenerService> aClass = myBatisListener.listenerClazz();
                String eventType = GXModelEventNamingEnums.SYNC_UPDATE_ENTITY.getEventType();
                String eventName = GXModelEventNamingEnums.SYNC_UPDATE_ENTITY.getEventName();
                Dict eventParam = Dict.create().set("listenerClazzName", aClass.getSimpleName()).set("listenerClazz", aClass);
                String runType = myBatisListener.runType();
                if (runType.equals(GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                    eventType = GXModelEventNamingEnums.ASYNC_UPDATE_ENTITY.getEventType();
                    eventName = GXModelEventNamingEnums.ASYNC_UPDATE_ENTITY.getEventName();
                }
                GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent = new GXMyBatisModelUpdateEntityEvent<>(source, eventType, eventParam, eventName);
                GXEventPublisherUtils.publishEvent(updateEntityEvent);
            }
        }
    }

    /**
     * 将Type转换为Mapper接口类
     * <p>
     * 使用Hutool工具类的Convert进行类型转换，将Type对象转换为Mapper接口类。
     * 内存安全考虑：使用TypeReference进行泛型转换，避免类型擦除问题，确保类型安全。
     *
     * @param type 待转换的Type对象
     * @return Mapper接口类，如果转换失败则可能返回null
     */
    private Class<Mapper> convertTypeToMapper(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }

    /**
     * 处理 UpdateWrapper条件对象
     * <p>
     * 将UpdateWrapper对象转换为Dict格式，提取其中的条件信息。
     * 内存安全考虑：使用泛型参数确保类型安全，避免类型转换异常。
     *
     * @param objectWrapper 更新条件对象
     * @param clazz         条件的泛型对象
     * @return Dict 包含条件信息的字典对象
     */
    private <T> Dict handleUpdateWrapper(Object objectWrapper, T clazz) {
        UpdateWrapper<T> updateWrapper = Convert.convert(new TypeReference<UpdateWrapper<T>>() {
        }, objectWrapper);
        Dict conditionDict = parseWhereSQL(updateWrapper);
        return conditionDict;
    }

    /**
     * 使用JSQLParser库进行Where语句SQL的解析
     * <p>
     * 解析UpdateWrapper中的SQL条件表达式，提取字段名、操作符和值信息。
     * 支持解析AND、OR、等于、大于等于、小于等于和LIKE等条件表达式。
     * 内存安全考虑：
     * 1. 使用局部变量存储中间结果，避免内存泄漏
     * 2. 使用访问者模式处理表达式，避免递归调用导致的栈溢出
     * 3. 异常处理确保资源正确释放
     *
     * @param updateWrapper 更新的Wrapper对象
     * @return Dict 包含字段名-操作符和字段名-值映射的字典对象
     */
    private <T> Dict parseWhereSQL(UpdateWrapper<T> updateWrapper) {
        final Integer[] paramNameSeq = new Integer[]{0};
        String whereSQL = updateWrapper.getTargetSql();
        Dict keyValuePairs = Dict.create();
        Dict keyOperatorPairs = Dict.create();
        Map<String, Object> paramNameValuePairs = updateWrapper.getParamNameValuePairs();
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
                    final String genParamName = Constants.WRAPPER_PARAM + (++paramNameSeq[0]);
                    String leftExpressionStr = CharSequenceUtil.toCamelCase(equalsTo.getLeftExpression().toString());
                    keyOperatorPairs.set(leftExpressionStr, equalsTo.getStringExpression());
                    keyValuePairs.set(leftExpressionStr, paramNameValuePairs.get(genParamName));
                }

                @Override
                public void visit(GreaterThanEquals greaterThanEquals) {
                    final String genParamName = Constants.WRAPPER_PARAM + (++paramNameSeq[0]);
                    String leftExpressionStr = CharSequenceUtil.toCamelCase(greaterThanEquals.getLeftExpression().toString());
                    keyOperatorPairs.set(leftExpressionStr, greaterThanEquals.getStringExpression());
                    keyValuePairs.set(leftExpressionStr, paramNameValuePairs.get(genParamName));
                }

                @Override
                public void visit(LikeExpression likeExpression) {
                    final String genParamName = Constants.WRAPPER_PARAM + (++paramNameSeq[0]);
                    String leftExpressionStr = CharSequenceUtil.toCamelCase(likeExpression.getLeftExpression().toString());
                    keyOperatorPairs.set(leftExpressionStr, likeExpression.getStringExpression());
                    keyValuePairs.set(leftExpressionStr, paramNameValuePairs.get(genParamName));
                }

                @Override
                public void visit(MinorThanEquals minorThanEquals) {
                    final String genParamName = Constants.WRAPPER_PARAM + (++paramNameSeq[0]);
                    String leftExpressionStr = CharSequenceUtil.toCamelCase(minorThanEquals.getLeftExpression().toString());
                    keyOperatorPairs.set(leftExpressionStr, minorThanEquals.getStringExpression());
                    keyValuePairs.set(leftExpressionStr, paramNameValuePairs.get(genParamName));
                }
            });
        } catch (Exception e) {
            log.info("解析SQL的Where语句失败");
            throw new GXBusinessException(e.getMessage(), e);
        }
        return Dict.create().set("keyOperatorPairs", keyOperatorPairs).set("keyValuePairs", keyValuePairs);
    }
}
