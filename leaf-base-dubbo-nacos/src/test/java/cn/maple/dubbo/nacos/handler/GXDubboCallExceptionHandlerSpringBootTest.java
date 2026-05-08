package cn.maple.dubbo.nacos.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void rpcExceptionAdviceHandlesRealMvcRequest() throws Exception {
        mockMvc.perform(get("/dubbo/rpc-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("Dubbo provider error, please contact operations."));
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
