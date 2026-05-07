package cn.maple.sso.web.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class GXSSODefaultHandler implements GXSSOHandler, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(GXSSODefaultHandler.class);

    private static final String JSON_CONTENT_TYPE = "application/json;charset=" + StandardCharsets.UTF_8.name();

    private static final String UNAUTHORIZED_MESSAGE = "Already logged out, please login again.";

    private static final AtomicReference<JSONConfig> JSON_CONFIG_REF = new AtomicReference<>();

    protected GXSSODefaultHandler() {
    }

    public static GXSSODefaultHandler getInstance() {
        return HandlerHolder.INSTANCE;
    }

    private static JSONConfig getJsonConfig() {
        JSONConfig config = JSON_CONFIG_REF.get();
        if (config == null) {
            config = new JSONConfig();
            config.setIgnoreNullValue(false);
            if (!JSON_CONFIG_REF.compareAndSet(null, config)) {
                config = JSON_CONFIG_REF.get();
            }
        }
        return config;
    }

    @Override
    public boolean preTokenIsNullAjax(HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");

        try {
            Dict data = Dict.create()
                    .set("code", HttpStatus.HTTP_UNAUTHORIZED)
                    .set("msg", UNAUTHORIZED_MESSAGE)
                    .set("data", null);

            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(JSON_CONTENT_TYPE);
            response.getWriter().write(JSONUtil.toJsonStr(data, getJsonConfig()));
        } catch (IOException e) {
            log.error("Handle unauthorized AJAX request failed with IO error.", e);
        } catch (Exception e) {
            log.error("Handle unauthorized AJAX request failed unexpectedly.", e);
        }
        return false;
    }

    @Override
    public boolean preTokenIsNull(HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");

        return true;
    }

    private static final class HandlerHolder {
        private static final GXSSODefaultHandler INSTANCE = new GXSSODefaultHandler();
    }
}
