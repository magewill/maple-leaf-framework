package cn.maple.core.framework.web.config;

import cn.hutool.core.map.MapUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.support.GXCustomerHandlerMethodArgumentResolver;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义参数解析器配置类
 * <p>
 * 该配置类负责注册所有实现了GXCustomerHandlerMethodArgumentResolver接口的自定义参数解析器。
 * 通过Spring的InitializingBean接口，在所有属性设置完成后自动执行初始化逻辑，
 * 将自定义的参数解析器添加到Spring MVC的参数解析器链中，并确保自定义解析器优先于默认解析器执行。
 */
@Configuration
public class GXHandlerMethodArgumentResolverConfigurer implements InitializingBean {
    /**
     * Spring MVC的请求映射处理适配器，用于管理HandlerMethodArgumentResolver
     */
    @Resource
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    /**
     * 在所有属性设置完成后执行的初始化方法
     * <p>
     * 该方法会从Spring容器中获取所有实现了GXCustomerHandlerMethodArgumentResolver接口的Bean，
     * 并将它们添加到RequestMappingHandlerAdapter的参数解析器列表中，确保自定义解析器优先执行。
     *
     * @throws Exception 初始化过程中可能抛出的异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 获取默认的参数解析器列表
        List<HandlerMethodArgumentResolver> defaultArgumentResolvers = requestMappingHandlerAdapter.getArgumentResolvers();
        // 创建新的参数解析器列表，用于存放自定义和默认的解析器
        List<HandlerMethodArgumentResolver> customArgumentResolvers = new ArrayList<>();

        // 从Spring容器中获取所有实现了GXCustomerHandlerMethodArgumentResolver接口的Bean
        Map<String, GXCustomerHandlerMethodArgumentResolver> customerHandlerMethodArgumentResolver = GXSpringContextUtils.getBeans(GXCustomerHandlerMethodArgumentResolver.class);
        if (MapUtil.isNotEmpty(customerHandlerMethodArgumentResolver)) {
            // 将所有自定义参数解析器添加到列表中
            customerHandlerMethodArgumentResolver.forEach((beanName, argumentResolver) -> customArgumentResolvers.add(argumentResolver));
        }
        // 确保默认参数解析器列表不为空
        assert defaultArgumentResolvers != null;
        // 将默认参数解析器添加到自定义参数解析器之后，确保自定义解析器优先执行
        customArgumentResolvers.addAll(defaultArgumentResolvers);
        // 注释掉的代码是用于去重的，目前未启用
        //List<HandlerMethodArgumentResolver> lastArgumentResolvers = CollUtil.distinct(customArgumentResolvers);
        // 设置新的参数解析器列表到RequestMappingHandlerAdapter中
        requestMappingHandlerAdapter.setArgumentResolvers(customArgumentResolvers);
    }
}
