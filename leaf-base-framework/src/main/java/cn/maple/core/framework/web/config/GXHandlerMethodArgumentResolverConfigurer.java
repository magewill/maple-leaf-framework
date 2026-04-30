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

@Configuration
public class GXHandlerMethodArgumentResolverConfigurer implements InitializingBean {
    @Resource
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    @Override
    public void afterPropertiesSet() throws Exception {
        List<HandlerMethodArgumentResolver> defaultArgumentResolvers = requestMappingHandlerAdapter.getArgumentResolvers();
        List<HandlerMethodArgumentResolver> customArgumentResolvers = new ArrayList<>();

        Map<String, GXCustomerHandlerMethodArgumentResolver> customerHandlerMethodArgumentResolver = GXSpringContextUtils.getBeans(GXCustomerHandlerMethodArgumentResolver.class);
        if (MapUtil.isNotEmpty(customerHandlerMethodArgumentResolver)) {
            customerHandlerMethodArgumentResolver.forEach((beanName, argumentResolver) -> customArgumentResolvers.add(argumentResolver));
        }
        assert defaultArgumentResolvers != null;
        customArgumentResolvers.addAll(defaultArgumentResolvers);
        //List<HandlerMethodArgumentResolver> lastArgumentResolvers = CollUtil.distinct(customArgumentResolvers);
        requestMappingHandlerAdapter.setArgumentResolvers(customArgumentResolvers);
    }
}
