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
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Provider-side exception filter that preserves declared exceptions and hides undeclared runtime details.
 */
@Activate(group = CommonConstants.PROVIDER)
public class GXDubboExceptionFilter extends ExceptionFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXDubboExceptionFilter.class);

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        if (appResponse.hasException() && GenericService.class != invoker.getInterface()) {
            try {
                Throwable exception = appResponse.getException();

                if (Objects.isNull(exception)) {
                    return;
                }

                if (isSentinelFlowException(exception)) {
                    Dict data = Dict.create().set("methodName", invocation.getMethodName())
                            .set("arguments", CollUtil.toList(invocation.getArguments()))
                            .set("interfaceName", invoker.getInterface());
                    exception = new GXSentinelFlowException("Service busy, please retry later.", HttpStatus.HTTP_NOT_ACCEPTABLE, data, exception);
                    appResponse.setException(exception);
                    return;
                }

                if (!(exception instanceof RuntimeException) && (exception instanceof Exception)) {
                    return;
                }

                if (CharSequenceUtil.equalsIgnoreCase(exception.getClass().getCanonicalName(), "org.mybatis.spring.MyBatisSystemException")) {
                    return;
                }

                if (exception instanceof GXBusinessException) {
                    return;
                }

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

                LOGGER.error("Got unchecked and undeclared exception called by {}. service: {}, method: {}, exception: {}: {}",
                        RpcContext.getServiceContext().getRemoteHost(), invoker.getInterface().getName(),
                        invocation.getMethodName(), exception.getClass().getName(), exception.getMessage(), exception);

                String serviceFile = ReflectUtils.getCodeBase(invoker.getInterface());
                String exceptionFile = ReflectUtils.getCodeBase(exception.getClass());
                if (serviceFile == null || exceptionFile == null || serviceFile.equals(exceptionFile)) {
                    return;
                }

                String className = exception.getClass().getName();
                if (className.startsWith("java.") || className.startsWith("jakarta.")) {
                    return;
                }

                if (exception instanceof RpcException) {
                    return;
                }

                if (exception instanceof RuntimeException) {
                    exception = new GXBusinessException("Provider error, please contact provider.", exception);
                }
                appResponse.setException(exception);
            } catch (Throwable e) {
                LOGGER.warn("Failed to run Dubbo exception filter called by {}. service: {}, method: {}, exception: {}: {}",
                        RpcContext.getServiceContext().getRemoteHost(), invoker.getInterface().getName(),
                        invocation.getMethodName(), e.getClass().getName(), e.getMessage(), e);
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
