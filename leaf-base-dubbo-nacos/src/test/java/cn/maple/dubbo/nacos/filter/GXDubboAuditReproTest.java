package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.handler.GXExceptionHandler;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.nacos.handler.GXDubboCallExceptionHandler;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.FatalBeanException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static cn.maple.dubbo.nacos.support.GXDubboTestSupport.appResponse;
import static cn.maple.dubbo.nacos.support.GXDubboTestSupport.invocation;
import static cn.maple.dubbo.nacos.support.GXDubboTestSupport.invoker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduction tests for the confirmed findings (FR-021). Each test asserts the desired behaviour
 * and therefore fails on the pre-fix code, except {@link #f01BusinessExceptionWrappedWhenBuiltInFilterActive}
 * which is a characterisation test documenting the defect (the built-in filter cannot be changed).
 */
class GXDubboAuditReproTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void f01BusinessExceptionWrappedWhenBuiltInFilterActive() {
        GXTraceIdContextUtils.putTraceId("repro-f01");
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create().set("orderId", "A-1"));
        AppResponse response = appResponse(business);
        Invoker<?> invoker = invoker(TestService.class);
        Invocation invocation = invocation("call");

        new ExceptionFilter().onResponse(response, invoker, invocation);
        assertFalse(response.getException() instanceof GXBusinessException,
                "built-in filter should have wrapped the business exception");

        new GXDubboExceptionFilter().onResponse(response, invoker, invocation);

        assertInstanceOf(RuntimeException.class, response.getException());
        assertFalse(response.getException() instanceof GXBusinessException,
                "business exception (code 4001 / data.orderId) is lost once the built-in filter wraps it");
        assertTrue(response.getException().getMessage().contains("business-error"),
                "only the message survives in the built-in wrapper");
    }

    @Test
    void f02AdviceShadowedByBaseHandler() throws Exception {
        Order order = GXDubboCallExceptionHandler.class.getAnnotation(Order.class);
        assertNotNull(order, "Dubbo advice must declare @Order so it wins over the catch-all base advice");
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReproController())
                .setControllerAdvice(new GXExceptionHandler(), new GXDubboCallExceptionHandler())
                .build();

        mockMvc.perform(get("/repro/rpc-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorType").exists());
    }

    @Test
    void f03SentinelDegradeNotConverted() {
        GXTraceIdContextUtils.putTraceId("repro-f03");
        AppResponse response = appResponse(new com.alibaba.csp.sentinel.slots.block.degrade.DegradeException("degrade"));

        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation("call"));

        assertInstanceOf(GXSentinelFlowException.class, response.getException());
    }

    @Test
    void f04SignatureUnresolvableFailsOpen() {
        GXTraceIdContextUtils.putTraceId("repro-f04");
        AppResponse response = appResponse(new FatalBeanException("boom"));
        Invocation mismatched = invocation("call", new Class<?>[]{String.class}, new Object[]{"x"});

        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), mismatched);

        GXBusinessException wrapped = assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals("providerRuntime", wrapped.getData().getStr("errorType"));
    }

    @Test
    void f13AgentsDocClaimsNoTests() throws IOException {
        String agents = Files.readString(Path.of("AGENTS.md"));

        assertFalse(agents.contains("当前模块下没有现成的 `src/test` 测试目录"),
                "AGENTS.md must be updated to reflect the existing 9 test classes");
        assertTrue(agents.contains("dubbo.provider.filter"), "AGENTS.md must document the mandatory provider filter config");
    }

    interface TestService {
        void call();
    }

    @RestController
    static class ReproController {
        @GetMapping("/repro/rpc-error")
        Dict rpcError() {
            throw new RpcException("rpc");
        }
    }
}
