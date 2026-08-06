package cn.maple.core.datasource.aspect;

import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.aspect.advisor.GXDataFilterInterfaceMethodAdvisor;
import cn.maple.core.datasource.dto.GXDataFilterContext;
import cn.maple.core.datasource.service.GXDataScopeService;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.aop.support.AopUtils;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDataFilterAspectTest {
    @AfterEach
    void clearContext() {
        GXDataFilterThreadLocalUtils.cleanDataFilterContext();
    }

    @Test
    void appliesInterfaceTypeAnnotationAndCleansContextAfterExecution() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            InterfaceTypeService service = context.getBean(InterfaceTypeService.class);

            assertEquals(" (u.dept_id in (7)) ", service.query());
            assertNull(GXDataFilterThreadLocalUtils.getDataFilterContext());
        }
    }

    @Test
    void cleansContextWhenInterfaceAnnotatedMethodThrows() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            InterfaceTypeService service = context.getBean(InterfaceTypeService.class);

            assertThrows(IllegalStateException.class, service::fail);
            assertNull(GXDataFilterThreadLocalUtils.getDataFilterContext());
        }
    }

    @Test
    void appliesChildInterfaceTypeAnnotationToInheritedMethod() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            ChildInterfaceService service = context.getBean(ChildInterfaceService.class);

            assertEquals(" (c.dept_id in (7)) ", service.query());
            assertNull(GXDataFilterThreadLocalUtils.getDataFilterContext());
        }
    }

    @Test
    void appliesInterfaceMethodAnnotationThroughCglibProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CglibMethodAnnotationConfig.class)) {
            InterfaceMethodService service = context.getBean(InterfaceMethodService.class);

            assertTrue(AopUtils.isCglibProxy(service));
            assertEquals(" (m.dept_id in (7)) ", service.query());
            assertNull(GXDataFilterThreadLocalUtils.getDataFilterContext());
        }
    }

    @Test
    void proceedsUnannotatedMethodWithoutDataScopeService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(NoScopeConfig.class)) {
            PlainService service = context.getBean(PlainService.class);

            assertEquals("ok", service.query());
            assertFalse(AopUtils.isAopProxy(service));
            assertNull(GXDataFilterThreadLocalUtils.getDataFilterContext());
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    @Import(GXApplicationContextAware.class)
    static class TestConfig {
        @Bean
        GXDataFilterAspect gxDataFilterAspect() {
            return new GXDataFilterAspect();
        }

        @Bean
        GXDataScopeService gxDataScopeService() {
            return new TestDataScopeService();
        }

        @Bean
        InterfaceTypeService interfaceTypeService() {
            return new InterfaceTypeServiceImpl();
        }

        @Bean
        ChildInterfaceService childInterfaceService() {
            return new ChildInterfaceServiceImpl();
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    @Import(GXApplicationContextAware.class)
    static class NoScopeConfig {
        @Bean
        GXDataFilterAspect gxDataFilterAspect() {
            return new GXDataFilterAspect();
        }

        @Bean
        PlainService plainService() {
            return new PlainService();
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(GXApplicationContextAware.class)
    static class CglibMethodAnnotationConfig {
        @Bean
        GXDataFilterAspect gxDataFilterAspect() {
            return new GXDataFilterAspect();
        }

        @Bean
        GXDataFilterInterfaceMethodAdvisor gxDataFilterInterfaceMethodAdvisor(GXDataFilterAspect gxDataFilterAspect) {
            return new GXDataFilterInterfaceMethodAdvisor(gxDataFilterAspect);
        }

        @Bean
        GXDataScopeService gxDataScopeService() {
            return new TestDataScopeService();
        }

        @Bean
        InterfaceMethodService interfaceMethodService() {
            return new InterfaceMethodServiceImpl();
        }
    }

    private static final class TestDataScopeService implements GXDataScopeService {
        @Override
        public Set<Number> getDeptIdLst() {
            return new LinkedHashSet<>(Set.of(7));
        }

        @Override
        public String getUserCondition(String tableAlias, String[] userIdFieldNames) {
            return null;
        }
    }

    @GXDataFilter(tableAlias = "u")
    interface InterfaceTypeService {
        String query();

        String fail();
    }

    static class InterfaceTypeServiceImpl implements InterfaceTypeService {
        @Override
        public String query() {
            GXDataFilterContext context = GXDataFilterThreadLocalUtils.getDataFilterContext();
            return context == null ? null : context.getSqlFilter();
        }

        @Override
        public String fail() {
            throw new IllegalStateException("expected");
        }
    }

    interface ParentInterfaceService {
        String query();
    }

    @GXDataFilter(tableAlias = "c")
    interface ChildInterfaceService extends ParentInterfaceService {
    }

    static class ChildInterfaceServiceImpl implements ChildInterfaceService {
        @Override
        public String query() {
            GXDataFilterContext context = GXDataFilterThreadLocalUtils.getDataFilterContext();
            return context == null ? null : context.getSqlFilter();
        }
    }

    interface InterfaceMethodService {
        @GXDataFilter(tableAlias = "m")
        String query();
    }

    static class InterfaceMethodServiceImpl implements InterfaceMethodService {
        @Override
        public String query() {
            GXDataFilterContext context = GXDataFilterThreadLocalUtils.getDataFilterContext();
            return context == null ? null : context.getSqlFilter();
        }
    }

    static class PlainService {
        public String query() {
            return "ok";
        }
    }
}
