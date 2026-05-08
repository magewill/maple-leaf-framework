package cn.maple.dubbo.nacos.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXResultUtils;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXDubboCallExceptionHandlerTest {
    private final GXDubboCallExceptionHandler handler = new GXDubboCallExceptionHandler();

    @Test
    void handlesRpcExceptionWithStableErrorCode() {
        GXResultUtils<Dict> result = handler.handleRpcException(new RpcException("rpc"));

        assertEquals(500, result.getCode());
        assertEquals("Dubbo provider error, please contact operations.", result.getMsg());
    }

    @Test
    void handlesSentinelFlowExceptionWithOriginalCodeMessageAndData() {
        Dict data = Dict.create().set("methodName", "call");
        GXSentinelFlowException exception = new GXSentinelFlowException("busy", 406, data);

        GXResultUtils<Dict> result = handler.handleSentinelFlowException(exception);

        assertEquals(406, result.getCode());
        assertEquals("busy", result.getMsg());
        assertEquals(data, result.getData());
    }
}
