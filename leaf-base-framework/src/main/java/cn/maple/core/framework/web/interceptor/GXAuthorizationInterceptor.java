package cn.maple.core.framework.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 登录用户验证拦截器
 *
 * @author britton
 */
@Slf4j
@SuppressWarnings("all")
public abstract class GXAuthorizationInterceptor implements AsyncHandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.debug("请自定义实现GXAuthorizationInterceptor类");
        // TODO 如果需要支持同一时间只能有一个客户端生效 可以在token中设置一个标识(eg : 时间戳、md5当前的Token...)
        // TODO 然后使用这个Token中的标识和存储器(缓存、数据库)中存储的相应Token中的标识对比即可
        return true;
    }
}
