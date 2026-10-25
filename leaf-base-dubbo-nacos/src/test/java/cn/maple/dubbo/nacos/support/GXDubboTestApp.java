package cn.maple.dubbo.nacos.support;

import cn.maple.core.framework.handler.GXExceptionHandler;
import cn.maple.dubbo.nacos.handler.GXDubboCallExceptionHandler;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Minimal Spring configuration for advice-ordering tests.
 *
 * <p>Deliberately avoids {@code @EnableAutoConfiguration} and {@code @ComponentScan("cn.maple")}
 * so that Nacos / datasource auto-configuration never starts (tests stay offline).
 */
@SpringBootConfiguration
@EnableWebMvc
@Import({GXExceptionHandler.class, GXDubboCallExceptionHandler.class})
public class GXDubboTestApp {
}
