package cn.maple.feign.support;

import cn.maple.feign.annotation.GXFeignHeader;
import feign.RequestLine;

/**
 * Feign annotation based clients used by real round-trip tests.
 */
public final class GXFeignTestClients {
    private GXFeignTestClients() {
    }

    public interface RoundTripClient {
        @RequestLine("GET /ok")
        String ok();

        @RequestLine("POST /write")
        String write();

        @GXFeignHeader(names = {"X-Tenant-Id", "Authorization", "Cookie"})
        @RequestLine("GET /annotated")
        String annotated();

        @RequestLine("GET /bad-request")
        String badRequest();

        @RequestLine("GET /unauthorized")
        String unauthorized();

        @RequestLine("GET /server-error")
        String serverError();

        @RequestLine("GET /teapot")
        String teapot();
    }
}
