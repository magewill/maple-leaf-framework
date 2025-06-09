/**
 * API错误响应数据传输对象
 * <p>
 * 该类用于封装API请求处理过程中发生错误时的标准响应格式，提供统一的错误信息结构。
 * 作为框架的标准错误响应对象，它继承自GXBaseApiResDto，包含了错误发生时间、HTTP状态码、
 * 错误码、错误消息以及请求路径等关键信息，便于前端统一处理错误情况。
 * </p>
 *
 * <p>
 * 主要应用场景：
 * <ul>
 *   <li>全局异常处理器中统一封装各类异常</li>
 *   <li>API接口中手动构建错误响应</li>
 *   <li>微服务间通信的错误信息传递</li>
 *   <li>安全认证和授权失败的响应</li>
 * </ul>
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在全局异常处理器中使用
 * @ExceptionHandler(GXBusinessException.class)
 * public GXErrorApiResDto handleBusinessException(GXBusinessException e, HttpServletRequest request) {
 *     GXErrorApiResDto errorResponse = new GXErrorApiResDto();
 *     errorResponse.setTimestamp(DateUtil.now());
 *     errorResponse.setStatus(HttpStatus.HTTP_INTERNAL_ERROR);
 *     errorResponse.setCode(e.getCode());
 *     errorResponse.setMessage(e.getMessage());
 *     errorResponse.setPath(request.getRequestURI());
 *     return errorResponse;
 * }
 *
 * // 2. 在业务代码中手动创建错误响应
 * @GetMapping("/resource/{id}")
 * public Object getResource(@PathVariable Long id) {
 *     if (resourceNotFound(id)) {
 *         GXErrorApiResDto errorResponse = new GXErrorApiResDto();
 *         errorResponse.setTimestamp(DateUtil.now());
 *         errorResponse.setStatus(HttpStatus.HTTP_NOT_FOUND);
 *         errorResponse.setCode(404);
 *         errorResponse.setMessage("资源不存在");
 *         errorResponse.setPath("/resource/" + id);
 *         return errorResponse;
 *     }
 *     // 正常业务逻辑...
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 与GXResultUtils的区别：
 * <ul>
 *   <li>GXErrorApiResDto专注于错误响应的标准化，包含更多错误相关的字段</li>
 *   <li>GXResultUtils是通用响应工具，同时处理成功和失败的情况</li>
 *   <li>在某些特定场景（如认证失败）中，框架会直接返回GXErrorApiResDto而非包装在GXResultUtils中</li>
 * </ul>
 * </p>
 *
 * <p>
 * 安全性考虑：
 * <ul>
 *   <li>在生产环境中，应避免将详细的技术错误信息暴露给客户端</li>
 *   <li>对于敏感操作的错误，建议使用通用错误消息，详细错误信息记录到日志中</li>
 *   <li>确保错误码的一致性，避免向客户端泄露系统内部状态</li>
 * </ul>
 * </p>
 */
package cn.maple.core.framework.api.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class GXHttpInvokerApiErrorResDto extends GXBaseApiResDto {
    /**
     * 错误发生的时间戳
     * <p>
     * 记录错误发生的精确时间，通常使用ISO-8601格式或自定义日期时间格式。
     * 该字段有助于问题排查和日志关联，特别是在分布式系统中追踪错误。
     * </p>
     */
    private String timestamp;

    /**
     * HTTP状态码
     * <p>
     * 符合HTTP协议规范的状态码，如404（资源不存在）、500（服务器内部错误）等。
     * 该字段帮助客户端快速识别错误类型，并采取相应的处理策略。
     * </p>
     * <p>
     * 常用状态码：
     * <ul>
     *   <li>400 - 错误的请求参数</li>
     *   <li>401 - 未授权（未登录）</li>
     *   <li>403 - 禁止访问（权限不足）</li>
     *   <li>404 - 资源不存在</li>
     *   <li>500 - 服务器内部错误</li>
     * </ul>
     * </p>
     */
    private int status;

    /**
     * 业务错误码
     * <p>
     * 应用自定义的业务错误码，用于更精确地标识错误类型。
     * 与HTTP状态码不同，业务错误码通常由应用自行定义，具有更强的业务语义。
     * </p>
     * <p>
     * 建议错误码设计遵循一定规范，如按模块划分范围，便于错误定位和统一管理。
     * 参考框架中的GXDefaultResultStatusCode枚举定义。
     * </p>
     */
    private int code;

    /**
     * 错误详细信息描述
     * <p>
     * 对错误的文字描述，提供给客户端展示或记录。
     * 在生产环境中，应避免包含敏感信息或过于技术化的错误详情。
     * </p>
     */
    private String message;

    /**
     * 出现错误的请求路径
     * <p>
     * 记录发生错误的API请求路径，有助于定位问题和统计错误分布。
     * 通常从HttpServletRequest中获取requestURI。
     * </p>
     */
    private String path;
}
