package cn.maple.dubbo.nacos.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.handler.GXExceptionHandler;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GXDubboCallExceptionHandler extends GXExceptionHandler {
    @ExceptionHandler(RpcException.class)
    public GXResultUtils<Dict> handleRpcException(RpcException e) {
        log.error(e.getMessage(), e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "服务端运行异常,请联系运维人员");
    }

    @ExceptionHandler(GXSentinelFlowException.class)
    public GXResultUtils<Dict> handleSentinelFlowException(GXSentinelFlowException e) {
        log.error(e.getMessage(), e);
        return GXResultUtils.error(e.getCode(), e.getMsg(), e.getData());
    }
}
