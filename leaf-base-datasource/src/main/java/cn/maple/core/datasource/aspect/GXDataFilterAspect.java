package cn.maple.core.datasource.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;
import cn.maple.core.datasource.service.GXDataScopeService;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 数据权限过滤，切面处理类
 * <p>
 * 该切面用于处理数据权限过滤，通过拦截标注了@GXDataFilter注解的方法，
 * 在方法执行前根据用户权限动态添加SQL过滤条件，实现数据权限控制。
 * 支持超级管理员不受数据权限限制的特性。
 *
 * @author 塵渊 britton@126.com
 */
@Aspect
@Component
@Slf4j
public class GXDataFilterAspect {
    /**
     * 定义数据过滤切点，拦截所有标注了@GXDataFilter注解的方法
     * 用于后续的前置通知、后置通知和异常通知的切点定义
     */
    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataFilter)")
    public void dataFilterPointCut() {
        // 切点定义，不需要实现
    }

    /**
     * 方法执行后和异常抛出后的处理
     * 用于清理ThreadLocal中的数据过滤条件，防止内存泄漏
     * 同时应用于正常执行完成和异常情况，确保在所有执行路径上都能正确释放资源
     */
    @After("dataFilterPointCut()")
    @AfterThrowing("dataFilterPointCut()")
    public void dataFilterAfter() {
        GXDataFilterInnerDto dataFilterInnerDto = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
        if (Objects.nonNull(dataFilterInnerDto) && CharSequenceUtil.isNotEmpty(dataFilterInnerDto.getSqlFilter())) {
            // 执行完成，要清除当前权限Sql，避免ThreadLocal资源泄漏
            GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
        }
    }

    /**
     * 方法执行前的处理
     * 根据用户权限获取数据过滤的SQL条件，并存入ThreadLocal中供后续查询使用
     * 超级管理员不受数据权限限制，直接跳过过滤条件设置
     *
     * @param point 切点对象，包含目标方法的相关信息
     * @throws GXBusinessException 当未实现GXDataScopeService接口或获取SQL过滤条件出错时抛出
     */
    @Before("dataFilterPointCut()")
    public void dataFilterBefore(JoinPoint point) {
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        if (Objects.isNull(dataScopeService)) {
            throw new GXBusinessException(CharSequenceUtil.format("请实现{}接口", GXDataScopeService.class.getName()));
        }
        boolean isSuperAdmin = dataScopeService.isSuperAdmin();
        // 如果是超级管理员，则不进行数据过滤
        if (isSuperAdmin) {
            return;
        }

        // 否则进行数据过滤
        try {
            String sqlFilter = getSqlFilter(point);
            if (CharSequenceUtil.isEmpty(sqlFilter)) {
                return;
            }
            GXDataFilterInnerDto dataScope = new GXDataFilterInnerDto(sqlFilter);
            GXDataFilterThreadLocalUtils.setDataFilterInnerDto(dataScope);
        } catch (Exception e) {
            throw new GXBusinessException(e.getMessage(), e);
        }
    }

    /**
     * 获取数据过滤的SQL条件
     * 从目标方法上获取@GXDataFilter注解，并通过GXDataScopeService接口获取对应的SQL过滤条件
     *
     * @param point 切点对象，包含目标方法的相关信息
     * @return 返回SQL过滤条件字符串，如果没有过滤条件则返回空字符串
     * @throws Exception 反射获取方法或调用GXDataScopeService.getSqlFilter方法时可能抛出异常
     */
    private String getSqlFilter(JoinPoint point) throws Exception {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = point.getTarget().getClass().getDeclaredMethod(signature.getName(), signature.getParameterTypes());
        GXDataFilter dataFilter = method.getAnnotation(GXDataFilter.class);
        GXDataScopeService dataScopeService = GXSpringContextUtils.getBean(GXDataScopeService.class);
        assert dataScopeService != null;
        return dataScopeService.getSqlFilter(dataFilter, point);
    }
}