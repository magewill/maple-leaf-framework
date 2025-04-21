package cn.maple.core.framework.web.support;

import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/**
 * 自定义参数解析器接口
 * <p>
 * 该接口扩展了Spring的HandlerMethodArgumentResolver接口，用于实现自定义的参数解析逻辑。
 * 实现此接口的类可以处理特定注解标记的方法参数，例如用户登录信息、权限信息等。
 * 在Spring MVC的请求处理过程中，实现类会被调用来解析和注入自定义参数。
 * </p>
 * 
 * @author maple
 * @since 1.0.0
 */
public interface GXCustomerHandlerMethodArgumentResolver extends HandlerMethodArgumentResolver {
}
