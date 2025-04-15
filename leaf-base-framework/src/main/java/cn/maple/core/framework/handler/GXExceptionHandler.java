package cn.maple.core.framework.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.code.GXDefaultResultStatusCode;
import cn.maple.core.framework.exception.*;
import cn.maple.core.framework.service.GXBotNotificationExceptionService;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GXExceptionHandler {
    @ExceptionHandler(MismatchedInputException.class)
    public GXResultUtils<String> handleMismatchedInputException(MismatchedInputException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.ok(HttpStatus.HTTP_INTERNAL_ERROR, "参数错误!");
    }

    @ExceptionHandler(GXBeanValidateException.class)
    public GXResultUtils<Dict> handleGXBeanValidateException(GXBeanValidateException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(e.getCode(), e.getMsg(), e.getData());
    }

    @ExceptionHandler(ClientAbortException.class)
    public GXResultUtils<String> handleClientAbortException(ClientAbortException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.ok(HttpStatus.HTTP_INTERNAL_ERROR, "IO异常");
    }

    @ExceptionHandler(InvalidFormatException.class)
    public GXResultUtils<String> handleInvalidFormatException(InvalidFormatException e) {
        log.error(e.getMessage(), e);
        String targetTypeSimpleName = e.getTargetType().getSimpleName();
        Object value = e.getValue();
        String[] msg = new String[]{""};
        e.getPath().forEach(path -> {
            String fieldName = path.getFieldName();
            msg[0] = CharSequenceUtil.format("{}字段的值{}为{}类型不能转换为{}类型,请提供正确的类型!", fieldName, value, value.getClass().getSimpleName(), targetTypeSimpleName);
        });
        exceptionNotify(e);
        return GXResultUtils.ok(HttpStatus.HTTP_INTERNAL_ERROR, msg[0]);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public GXResultUtils<String> handlerNoHandlerFoundException(NoHandlerFoundException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(404, "路径不存在，请检查路径是否正确");
    }

    @ExceptionHandler(BindException.class)
    public GXResultUtils<Map<String, Object>> handleBindException(BindException e) {
        log.error(e.getMessage(), e);
        Map<String, Object> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        exceptionNotify(e);
        return GXResultUtils.error(GXDefaultResultStatusCode.INTERNAL_SYSTEM_ERROR, errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public GXResultUtils<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, Object> errors = new HashMap<>();
        String firstErrorKey = null;
        Dict data = Dict.create();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
            if (CharSequenceUtil.isEmpty(firstErrorKey)) {
                firstErrorKey = error.getField();
                data.set(firstErrorKey, error.getDefaultMessage());
            }
        }
        log.error(e.getMessage(), e);
        Object orDefault = errors.getOrDefault(firstErrorKey, GXDefaultResultStatusCode.PARAMETER_VALIDATION_ERROR.getMsg());
        exceptionNotify(e);
        return GXResultUtils.error(GXDefaultResultStatusCode.PARAMETER_VALIDATION_ERROR.getCode(), orDefault.toString(), data);
    }

    @ExceptionHandler(ValidationException.class)
    public GXResultUtils<Map<String, Object>> handleValidationException(ValidationException e) {
        log.error(e.getMessage(), e);
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("msg", e.getCause().getMessage());
        exceptionNotify(e);
        return GXResultUtils.error(GXDefaultResultStatusCode.INTERNAL_SYSTEM_ERROR, hashMap);
    }

    @ExceptionHandler(MultipartException.class)
    public GXResultUtils<String> handleMultipartException(MultipartException e, RedirectAttributes redirectAttributes) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, e.getCause().getMessage());
    }

    @ExceptionHandler(GXTokenInvalidException.class)
    public GXResultUtils<String> handleGXTokenInvalidException(GXTokenInvalidException e, RedirectAttributes redirectAttributes) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(SQLException.class)
    public GXResultUtils<String> handleSQLException(SQLException e, RedirectAttributes redirectAttributes) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "数据异常,请联系相关人员!");
    }

    @ExceptionHandler(SQLSyntaxErrorException.class)
    public GXResultUtils<String> handleSQLSyntaxErrorException(SQLSyntaxErrorException e, RedirectAttributes redirectAttributes) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "数据异常,请联系相关人员!");
    }

    @ExceptionHandler(GXDBNotExistsException.class)
    public GXResultUtils<String> handleGXDBNotExistsException(GXDBNotExistsException e, RedirectAttributes redirectAttributes) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(GXDBExistsException.class)
    public GXResultUtils<String> handleGXDBExistsException(GXDBExistsException e, RedirectAttributes redirectAttributes) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(GXDataFormatIncorrectException.class)
    public GXResultUtils<String> handleGXDataFormatIncorrectException(GXDataFormatIncorrectException e, RedirectAttributes redirectAttributes) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(GXBusinessException.class)
    public GXResultUtils<Dict> handleBusinessException(GXBusinessException e) {
        log.error(e.getMessage(), e);
        Dict data = e.getData();
        if (Objects.nonNull(e.getCause())) {
            data = ((GXBusinessException) e.getCause()).getData();
        }
        exceptionNotify(e);
        return GXResultUtils.error(e.getCode(), e.getMsg(), data);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public GXResultUtils<Dict> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.error(e.getMessage(), e);
        String parameterName = e.getParameterName();
        exceptionNotify(e);
        return GXResultUtils.error(GXDefaultResultStatusCode.PARAMETER_VALIDATION_ERROR.getCode(), CharSequenceUtil.format("缺失{}参数", parameterName), Dict.create().set(parameterName, CharSequenceUtil.format("参数{}必填", parameterName)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public GXResultUtils<Dict> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error(e.getMessage(), e);
        String message = e.getMessage();
        Dict dict = Dict.create();
        if (CharSequenceUtil.contains(message, StrPool.COLON)) {
            String[] split = CharSequenceUtil.splitToArray(message, StrPool.COLON.charAt(0), 2);
            String concat = CharSequenceUtil.concat(true, split);
            dict.set(split[0], concat);
        } else {
            dict.set("field", message);
        }
        exceptionNotify(e);
        return GXResultUtils.error(GXDefaultResultStatusCode.PARAMETER_VALIDATION_ERROR.getCode(), GXDefaultResultStatusCode.PARAMETER_VALIDATION_ERROR.getMsg(), dict);
    }

    @ExceptionHandler(Exception.class)
    public GXResultUtils<Dict> handleException(Exception e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "系统内部错误");
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public GXResultUtils<Dict> handleSQLIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "数据已经存在");
    }

    @ExceptionHandler(UnexpectedTypeException.class)
    public GXResultUtils<Dict> handleUnexpectedTypeException(UnexpectedTypeException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "使用的数据验证器不能验证请求的参数!");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public GXResultUtils<Dict> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_BAD_METHOD, "不支持的HTTP请求方法!");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public GXResultUtils<Dict> handleNoResourceFoundException(NoResourceFoundException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_NOT_FOUND, "No static resource");
    }

    @ExceptionHandler(GXConvertException.class)
    public GXResultUtils<Dict> handleGXConvertException(GXConvertException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "数据转换错误!");
    }

    @ExceptionHandler(GXSqlInjectionException.class)
    public GXResultUtils<Dict> handleGXSqlInjectionException(GXSqlInjectionException e) {
        log.error(e.getMessage(), e);
        exceptionNotify(e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "存在SQL注入风险!");
    }

    /**
     * 发布异常通知信息事件
     *
     * @param throwable 异常信息
     */
    private void exceptionNotify(Throwable throwable) {
        GXBotNotificationExceptionService botNotificationExceptionService = GXSpringContextUtils.getBean(GXBotNotificationExceptionService.class);
        if (Objects.nonNull(botNotificationExceptionService)) {
            botNotificationExceptionService.botNotificationException(throwable);
        }
    }
}
