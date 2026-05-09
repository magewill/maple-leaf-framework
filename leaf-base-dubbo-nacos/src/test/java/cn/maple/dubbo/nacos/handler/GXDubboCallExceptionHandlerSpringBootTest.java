package cn.maple.dubbo.nacos.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GXDubboCallExceptionHandlerSpringBootTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GXDubboCallExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void rpcExceptionAdviceHandlesRealMvcRequest() throws Exception {
        GXTraceIdContextUtils.putTraceId("mvc-trace");

        mockMvc.perform(get("/dubbo/rpc-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("Dubbo provider error, please contact operations."))
                .andExpect(jsonPath("$.data.X-B3-TraceId").value("mvc-trace"))
                .andExpect(jsonPath("$.data.errorType").value("unknown"))
                .andExpect(jsonPath("$.data.rpcMessage").value("RPC failed"));
    }

    @Test
    void sentinelFlowAdviceHandlesRealMvcRequest() throws Exception {
        mockMvc.perform(get("/dubbo/flow-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(406))
                .andExpect(jsonPath("$.msg").value("busy"))
                .andExpect(jsonPath("$.data.methodName").value("call"));
    }

    @RestController
    static class TestController {
        @GetMapping("/dubbo/rpc-error")
        Dict rpcError() {
            throw new RpcException("rpc");
        }

        @GetMapping("/dubbo/flow-error")
        Dict flowError() {
            throw new GXSentinelFlowException("busy", 406, Dict.create().set("methodName", "call"));
        }
    }
}
