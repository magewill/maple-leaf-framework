package cn.maple.webclient.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.util.GXResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@RestControllerAdvice
@Slf4j
public class GXWebClientExceptionHandler {
    @ExceptionHandler(WebClientRequestException.class)
    public GXResultUtils<Dict> handleWebClientRequestException(WebClientRequestException e) {
        log.error(e.getMessage(), e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "WebClient网络请求异常");
    }
}
