package cn.maple.dubbo.nacos.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.handler.GXExceptionHandler;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.nacos.support.GXDubboTestApp;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring context test: both the base {@code GXExceptionHandler} and the Dubbo
 * {@code GXDubboCallExceptionHandler} are registered as advice. The Dubbo advice must win for
 * {@code RpcException} / {@code GXSentinelFlowException}.
 */
@SpringBootTest(
        classes = {GXDubboTestApp.class, GXDubboCallExceptionHandlerSpringBootTest.TestController.class},
        properties = {
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        }
)
class GXDubboCallExceptionHandlerSpringBootTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void bothAdvicesAreRegisteredInTheSameContext() {
        assertTrue(context.getBeanNamesForType(GXExceptionHandler.class).length > 0, "base advice must be present");
        assertTrue(context.getBeanNamesForType(GXDubboCallExceptionHandler.class).length > 0, "dubbo advice must be present");
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
