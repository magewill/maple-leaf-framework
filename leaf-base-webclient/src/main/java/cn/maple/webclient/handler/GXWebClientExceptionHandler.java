package cn.maple.webclient.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.util.GXResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
@Slf4j
public class GXWebClientExceptionHandler {
    @ExceptionHandler(WebClientRequestException.class)
    public GXResultUtils<Dict> handleWebClientRequestException(WebClientRequestException e) {
        log.error(e.getMessage(), e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "WebClient网络请求异常");
    }

    @ExceptionHandler(WebClientResponseException.class)
    public GXResultUtils<Dict> handleWebClientResponseException(WebClientResponseException e) {
        log.error("WebClient response error, statusCode={}, responseBody={}",
                e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        return GXResultUtils.error(e.getStatusCode().value(), "WebClient remote service response exception");
    }
}
