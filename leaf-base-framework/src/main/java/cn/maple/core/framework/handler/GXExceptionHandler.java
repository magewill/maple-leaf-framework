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
 * 全局异常处理器
 * <p>
 * 该类负责捕获并处理Spring MVC应用中未被捕获的异常，将其转换为统一的响应格式返回给客户端。
 * 通过@RestControllerAdvice注解，对所有Controller抛出的异常进行集中处理，避免异常信息直接暴露给用户。
 * 支持处理多种类型的异常，包括参数验证异常、业务逻辑异常、数据库异常、认证授权异常等。
 * 同时，提供异常通知功能，可将异常信息发送给监控系统或管理员。
 * </p>
 *
 * <p>
 * 主要功能：
 * - 统一异常响应格式，提供一致的API错误处理体验
 * - 隐藏系统内部错误细节，增强系统安全性
 * - 详细记录异常日志，便于问题定位和诊断
 * - 支持多种类型异常的个性化处理
 * - 提供异常通知机制，实现异常的实时监控
 * </p>
 *
 * <p>
 * 使用示例：
 * 1. 在Controller中可以直接抛出异常，由全局异常处理器统一处理：
 * <pre>
 * @RestController
 * @RequestMapping("/api/users")
 * public class UserController {
 *     @GetMapping("/{id}")
 *     public UserVO getUser(@PathVariable Long id) {
 *         User user = userService.getById(id);
 *         if (user == null) {
 *             // 抛出业务异常，将由GXExceptionHandler处理并转换为统一响应格式
 *             throw new GXBusinessException("用户不存在");
 *         }
 *         return convertToVO(user);
 *     }
 * }
 * </pre>
 * <p>
 * 2. 客户端将收到统一格式的错误响应：
 * <pre>
 * {
 *     "code": 500,
 *     "msg": "用户不存在",
 *     "data": null,
 *     "success": false
 * }
 * </pre>
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
@RestControllerAdvice
@Slf4j
public class GXExceptionHandler {
    /**
     * 处理JSON输入不匹配异常
     * <p>
     * 当请求体中的JSON数据与目标对象的结构不匹配时，会抛出MismatchedInputException异常。
     * 例如，JSON中的字段类型与Java对象的属性类型不兼容，或者JSON结构与预期不符。
     * </p>
     * <p>
     * 常见场景：
     * - 前端传递的JSON字段类型与后端实体类属性类型不匹配
     * - JSON格式错误或不完整
     * - 使用了不支持的数据格式
     * </p>
     *
     * @param e 捕获到的MismatchedInputException异常
     * @return 统一格式的错误响应，状态码为500，消息为"参数错误!"
     */
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
     * <p>
     * 该方法负责将捕获到的异常信息通过机器人通知服务发送给相关人员或系统。
     * 通过Spring容器获取GXBotNotificationExceptionService的实例，如果该服务存在，
     * 则调用其botNotificationException方法发送异常通知。
     * </p>
     * <p>
     * 通知机制的主要作用：
     * 1. 实时监控系统运行状态，及时发现异常
     * 2. 自动将异常信息推送给开发人员或运维人员
     * 3. 对重要异常进行记录和跟踪，便于后续分析和处理
     * 4. 减少人工巡检的工作量，提高系统监控效率
     * </p>
     * <p>
     * 使用场景：
     * - 生产环境中的关键异常实时通知
     * - 重要业务流程的异常监控
     * - 系统安全相关异常的及时预警
     * - 性能问题和资源耗尽的提前预警
     * </p>
     * <p>
     * 注意事项：
     * - 该方法不抛出异常，确保通知机制本身不会影响正常的异常处理流程
     * - 通知服务可能不存在（如开发环境或测试环境），此时会安全地跳过通知步骤
     * - 通知内容中可能包含敏感信息，应确保通知渠道的安全性
     * </p>
     *
     * @param throwable 需要通知的异常信息对象
     */
    private void exceptionNotify(Throwable throwable) {
        try {
            // 从Spring容器中获取机器人通知服务
            GXBotNotificationExceptionService botNotificationExceptionService =
                    GXSpringContextUtils.getBean(GXBotNotificationExceptionService.class);

            // 如果通知服务存在，则发送异常通知
            if (Objects.nonNull(botNotificationExceptionService)) {
                botNotificationExceptionService.botNotificationException(throwable);
                log.debug("已发送异常通知: {}", throwable.getMessage());
            }
        } catch (Exception e) {
            // 确保通知机制本身的异常不会影响正常的异常处理流程
            log.warn("发送异常通知时发生错误: {}", e.getMessage());
        }
    }
}
