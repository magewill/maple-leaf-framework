package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Dubbo服务提供方的异常处理过滤器，负责处理和转换RPC调用过程中产生的异常
 * <p>
 * 该过滤器通过Dubbo的SPI机制自动加载，在服务提供方处理请求时被调用。
 * 主要职责是对服务执行过程中产生的异常进行分类处理，确保异常信息能够以合适的方式传递给客户端，
 * 同时保护服务提供方的内部实现细节不被泄露。
 * </p>
 * <p>
 * 异常处理策略：
 * 1. 对于Checked异常（非RuntimeException的Exception）：直接抛出，不做处理
 * 2. 对于MyBatisSystemException：直接抛出，保留原始异常信息
 * 3. 对于Sentinel限流异常：转换为GXSentinelFlowException，提供友好的错误提示
 * 4. 对于自定义业务异常（GXBusinessException）：直接抛出，保留原始异常信息
 * 5. 对于接口方法声明的异常：直接抛出，符合接口契约
 * 6. 对于未在方法签名中声明的异常：记录错误日志，并根据异常来源决定处理方式
 * 7. 对于其他RuntimeException：包装为GXBusinessException，隐藏实现细节
 * </p>
 * <p>
 * 线程安全说明：
 * - 本过滤器基于Dubbo的Filter机制，每个请求由独立线程处理，不存在线程安全问题
 * - 过滤器中不维护任何共享状态，所有操作都基于方法参数进行
 * - 日志记录使用线程安全的Logger实例
 * </p>
 * 
 * @author gapleaf@163.com
 */
@Activate(group = CommonConstants.PROVIDER)
public class GXDubboExceptionFilter extends ExceptionFilter {
    /**
     * 日志对象，用于记录异常处理过程中的关键信息
     */
    private final Logger logger = LoggerFactory.getLogger(GXDubboExceptionFilter.class);

    /**
     * 处理Dubbo服务提供方的响应，对异常进行分类处理
     * <p>
     * 该方法在服务执行完毕后被调用，负责检查响应中是否包含异常，并根据异常类型进行不同的处理。
     * 主要目的是确保异常能够以合适的方式传递给客户端，同时保护服务提供方的内部实现细节。
     * </p>
     * <p>
     * 异常处理流程：
     * 1. 检查响应是否包含异常，且调用的不是GenericService
     * 2. 根据异常类型（Checked异常、MyBatis异常、Sentinel限流异常、业务异常等）进行分类处理
     * 3. 对于未在方法签名中声明的异常，记录详细的错误日志
     * 4. 根据异常的来源（同一个jar包、JDK、Dubbo框架等）决定是否需要包装异常
     * 5. 对于需要包装的RuntimeException，转换为GXBusinessException，提供友好的错误提示
     * </p>
     * <p>
     * 线程安全说明：
     * - 每个请求由独立线程处理，不存在线程安全问题
     * - 方法内不维护任何共享状态，所有操作都基于方法参数进行
     * - 异常处理逻辑被try-catch包裹，确保即使在处理异常过程中出现问题，也不会影响Dubbo框架的正常运行
     * </p>
     *
     * @param appResponse 服务执行的响应结果，可能包含异常
     * @param invoker 服务调用器，包含服务接口信息
     * @param invocation 调用信息，包含方法名、参数等
     */
    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        if (appResponse.hasException() && GenericService.class != invoker.getInterface()) {
            try {
                Throwable exception = appResponse.getException();

                if (Objects.isNull(exception)) {
                    return;
                }

                // 1. 处理Sentinel限流异常
                // BlockException是Checked异常，必须在通用Checked异常分支之前处理。
                if (isSentinelFlowException(exception)) {
                    Dict data = Dict.create().set("methodName", invocation.getMethodName())
                            .set("arguments", CollUtil.toList(invocation.getArguments()))
                            .set("interfaceName", invoker.getInterface());
                    exception = new GXSentinelFlowException("服务繁忙,请稍后重试!!", HttpStatus.HTTP_NOT_ACCEPTABLE, data, exception);
                    appResponse.setException(exception);
                    return;
                }

                // 2. 处理Checked异常（非RuntimeException的Exception）
                // 对于Checked异常，直接抛出，不做处理，因为这类异常通常是业务预期内的异常
                if (!(exception instanceof RuntimeException) && (exception instanceof Exception)) {
                    return;
                }

                // 3. 处理MyBatisSystemException异常
                // 对于MyBatis框架抛出的系统异常，直接返回，保留原始异常信息，便于定位数据库相关问题
                if (CharSequenceUtil.equalsIgnoreCase(exception.getClass().getCanonicalName(), "org.mybatis.spring.MyBatisSystemException")) {
                    return;
                }

                // 4. 处理自定义业务异常
                // 对于应用自定义的业务异常（GXBusinessException），直接抛出，保留原始异常信息
                if (exception instanceof GXBusinessException) {
                    return;
                }

                // 5. 处理接口方法声明的异常
                // 对于在接口方法签名中声明的异常类型，直接抛出，符合接口契约
                try {
                    Method method = invoker.getInterface().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
                    Class<?>[] exceptionClasses = method.getExceptionTypes();
                    for (Class<?> exceptionClass : exceptionClasses) {
                        if (exceptionClass.isAssignableFrom(exception.getClass())) {
                            return;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    return;
                }

                // 6. 处理未在方法签名中声明的异常
                // 对于未在方法签名中声明的异常，记录详细的错误日志，包括调用方IP、服务名、方法名和异常信息
                logger.error("[Dubbo服务调用出错]Got unchecked and undeclared exception which called by " + RpcContext.getServiceContext().getRemoteHost() + ". service: " + invoker.getInterface().getName() + ", method: " + invocation.getMethodName() + ", exception: " + exception.getClass().getName() + ": " + exception.getMessage(), exception);

                // 7. 处理与接口在同一个jar包中的异常
                // 如果异常类与接口类在同一个jar包中，说明是服务开发者有意抛出的异常，直接抛出
                String serviceFile = ReflectUtils.getCodeBase(invoker.getInterface());
                String exceptionFile = ReflectUtils.getCodeBase(exception.getClass());
                if (serviceFile == null || exceptionFile == null || serviceFile.equals(exceptionFile)) {
                    return;
                }
                // 8. 处理JDK内置异常
                // 对于来自Java标准库的异常，直接抛出，这类异常通常具有明确的语义
                String className = exception.getClass().getName();
                if (className.startsWith("java.") || className.startsWith("jakarta.")) {
                    return;
                }

                // 9. 处理Dubbo框架异常
                // 对于Dubbo框架自身的异常，直接抛出，这类异常通常与RPC调用机制相关
                if (exception instanceof RpcException) {
                    return;
                }

                // 10. 处理其他RuntimeException
                // 对于不属于以上类别的RuntimeException，包装为GXBusinessException，提供友好的错误提示，同时隐藏实现细节
                if (exception instanceof RuntimeException) {
                    exception = new GXBusinessException("服务方出现错误,请联系服务方!!", exception);
                }
                appResponse.setException(exception);
            } catch (Throwable e) {
                logger.warn("[Dubbo服务调用出错]Fail to ExceptionFilter when called by " + RpcContext.getServiceContext().getRemoteHost() + ". service: " + invoker.getInterface().getName() + ", method: " + invocation.getMethodName() + ", exception: " + e.getClass().getName() + ": " + e.getMessage(), e);
            }
        }
    }

    private boolean isSentinelFlowException(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (CharSequenceUtil.equals(className, "com.alibaba.csp.sentinel.slots.block.flow.FlowException")
                    || CharSequenceUtil.equals(className, "com.alibaba.csp.sentinel.slots.block.BlockException")
                    || CharSequenceUtil.contains(message, "SentinelBlockException: FlowException")
                    || CharSequenceUtil.contains(message, "FlowException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
