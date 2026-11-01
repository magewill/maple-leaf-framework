package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.nacos.selector.GXPenetrateAttachmentSelector;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of the module filter chain over a real Dubbo invocation path.
 *
 * <p>Unlike the other tests in this module, the provider is exported and referenced through Dubbo
 * itself (in-JVM protocol, no registry), so the SPI extension files
 * ({@code META-INF/dubbo/org.apache.dubbo.rpc.Filter}), the {@code -exception} exclusion and the
 * provider/consumer activation groups are exercised as at runtime. No external middleware is needed.
 */
class GXDubboFilterChainEndToEndTest {
    private static final String PROVIDER_FILTER = "dubboExceptionFilter,-exception";

    private static ServiceConfig<EchoService> serviceConfig;
    private static ReferenceConfig<EchoService> referenceConfig;
    private static EchoService echoService;

    @BeforeAll
    static void exportAndReference() {
        ApplicationConfig application = new ApplicationConfig("gx-dubbo-e2e");
        application.setQosEnable(false);
        RegistryConfig registry = new RegistryConfig(RegistryConfig.NO_AVAILABLE);

        serviceConfig = new ServiceConfig<>();
        serviceConfig.setApplication(application);
        serviceConfig.setRegistry(registry);
        serviceConfig.setProtocol(new ProtocolConfig("injvm"));
        serviceConfig.setScope("local");
        serviceConfig.setInterface(EchoService.class);
        serviceConfig.setRef(new EchoServiceImpl());
        serviceConfig.setFilter(PROVIDER_FILTER);
        serviceConfig.export();

        referenceConfig = new ReferenceConfig<>();
        referenceConfig.setApplication(application);
        referenceConfig.setRegistry(registry);
        referenceConfig.setInterface(EchoService.class);
        referenceConfig.setScope("local");
        referenceConfig.setCheck(false);
        referenceConfig.setTimeout(5000);
        echoService = referenceConfig.get();
    }

    @AfterAll
    static void release() {
        // Reference first, then the export; the statics are nulled so a second use fails fast instead of
        // silently talking to a destroyed invoker.
        if (referenceConfig != null) {
            referenceConfig.destroy();
            referenceConfig = null;
        }
        if (serviceConfig != null) {
            serviceConfig.unexport();
            serviceConfig = null;
        }
        echoService = null;
    }

    @AfterEach
    void tearDown() {
        RpcContext.removeContext();
        MDC.clear();
        GXPenetrateAttachmentSelector.resetAppNameCache();
    }

    @Test
    void keepsBusinessCodeAndDataOverRealChain() {
        GXTraceIdContextUtils.putTraceId("e2e-trace-business");

        GXBusinessException exception = assertThrows(GXBusinessException.class, () -> echoService.throwBusiness());

        assertEquals(4001, exception.getCode());
        assertEquals("business-error", exception.getMsg());
        assertEquals("A-1", exception.getData().getStr("orderId"));
        assertEquals("e2e-trace-business", exception.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void wrapsUndeclaredRuntimeExceptionOverRealChain() {
        GXTraceIdContextUtils.putTraceId("e2e-trace-undeclared");

        GXBusinessException exception = assertThrows(GXBusinessException.class, () -> echoService.throwUndeclared());

        assertEquals(500, exception.getCode());
        assertEquals("providerRuntime", exception.getData().getStr("errorType"));
        assertEquals("e2e-trace-undeclared", exception.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void convertsSentinelBlockExceptionOverRealChain() {
        GXTraceIdContextUtils.putTraceId("e2e-trace-sentinel");

        GXSentinelFlowException exception = assertThrows(GXSentinelFlowException.class, () -> echoService.throwSentinelBlock());

        assertEquals(406, exception.getCode());
        assertEquals("sentinelFlow", exception.getData().getStr("errorType"));
    }

    @Test
    void propagatesTraceIdToProviderAndRestoresCaller() {
        GXTraceIdContextUtils.putTraceId("e2e-trace-propagation");

        assertEquals("e2e-trace-propagation", echoService.observedTraceId());
        assertEquals("e2e-trace-propagation", GXTraceIdContextUtils.getTraceId());
    }

    /**
     * Locks the boundary of this module's {@code RpcContext} writes between same-thread calls.
     *
     * <p>Verified facts on a real chain: Dubbo's {@code ContextFilter} clears the client attachment when
     * the invocation completes, but the consumer-side {@code getServerAttachment()} keeps the TraceId
     * written by the module filter (that write is part of the P-OK-01 baseline and must stay visible).
     * Because {@code resolveTraceId} prefers attachments over the ThreadLocal, the next call on the same
     * thread would reuse the previous TraceId until the Dubbo context is reset with
     * {@code RpcContext.removeContext()}. This boundary is registered in {@code AGENTS.md} §3 and
     * {@code contracts/dubbo-trace-context-contract.md} §1; the assertions below fail if either side
     * changes silently.
     */
    @Test
    void documentsThreadContextBoundaryBetweenSameThreadCalls() {
        GXTraceIdContextUtils.putTraceId("e2e-trace-first");
        assertEquals("e2e-trace-first", echoService.observedTraceId(), "first call must use the caller TraceId");

        assertNull(RpcContext.getClientAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY),
                "Dubbo ContextFilter must clear the client attachment after the invocation");
        assertEquals("e2e-trace-first", RpcContext.getServerAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY),
                "the P-OK-01 baseline keeps the TraceId on the server attachment after the invocation");

        RpcContext.removeContext();
        GXTraceIdContextUtils.putTraceId("e2e-trace-second");

        assertEquals("e2e-trace-second", echoService.observedTraceId(),
                "after resetting the Dubbo context the next call must use its own TraceId");
        assertEquals("e2e-trace-second", GXTraceIdContextUtils.getTraceId());
    }

    @Test
    void generatesTraceIdWhenCallerHasNone() {
        String providerTraceId = echoService.observedTraceId();

        assertTrue(providerTraceId != null && providerTraceId.length() == 32, "provider must receive a generated TraceId");
        assertEquals("", GXTraceIdContextUtils.getTraceId(),
                "caller context entered empty, so the finally block must clear the generated TraceId again");
    }

    /**
     * Provider side of the chain: the undeclared exceptions must be classified by the module filter,
     * never by the provider code.
     */
    private static final class EchoServiceImpl implements EchoService {
        @Override
        public String observedTraceId() {
            return GXTraceIdContextUtils.getTraceId();
        }

        @Override
        public void throwBusiness() {
            throw new GXBusinessException("business-error", 4001, Dict.create().set("orderId", "A-1"));
        }

        @Override
        public void throwUndeclared() {
            throw new ProviderInternalException("provider-internal-detail");
        }

        @Override
        public void throwSentinelBlock() {
            throw new com.alibaba.csp.sentinel.slots.block.degrade.DegradeException("degrade");
        }
    }

    /**
     * Undeclared, non-JDK runtime exception: {@code java.*} exceptions are passed through on purpose,
     * so the wrapping branch needs an exception from the application codebase.
     */
    static class ProviderInternalException extends RuntimeException {
        ProviderInternalException(String message) {
            super(message);
        }
    }

    /**
     * Provider contract used by the chain: the throwing methods deliberately declare no checked
     * exception so the module filter owns the classification.
     */
    public interface EchoService {
        String observedTraceId();

        void throwBusiness();

        void throwUndeclared();

        void throwSentinelBlock();
    }
}
