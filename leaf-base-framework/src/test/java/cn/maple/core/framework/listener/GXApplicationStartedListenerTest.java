package cn.maple.core.framework.listener;

import cn.maple.core.framework.annotation.GXPermission;
import cn.maple.core.framework.annotation.GXPermissionCtl;
import cn.maple.core.framework.dto.inner.permission.GXBasePermissionInnerDto;
import cn.maple.core.framework.event.GXPermissionEvent;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GXApplicationStartedListener 权限收集监听器测试")
class GXApplicationStartedListenerTest {
    private GXApplicationStartedListener listener;
    private ApplicationStartedEvent mockEvent;
    private ConfigurableApplicationContext mockContext;
    private ConfigurableListableBeanFactory mockBeanFactory;
    private MockedStatic<GXEventPublisherUtils> mockedEventPublisher;

    @BeforeEach
    void setUp() {
        listener = new GXApplicationStartedListener();
        mockContext = mock(ConfigurableApplicationContext.class);
        mockBeanFactory = mock(ConfigurableListableBeanFactory.class);
        mockEvent = mock(ApplicationStartedEvent.class);
        when(mockEvent.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getBeanFactory()).thenReturn(mockBeanFactory);
        mockedEventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class);
    }

    @AfterEach
    void tearDown() {
        listener.destroy();
        mockedEventPublisher.close();
    }

    @Test
    @DisplayName("没有权限控制 Bean 时不发布权限事件")
    void shouldNotPublishEventWhenNoPermissionBeans() {
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class)).thenReturn(new LinkedHashMap<>());

        listener.onApplicationEvent(mockEvent);

        mockedEventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), never());
    }

    @Test
    @DisplayName("收集方法权限并应用类级默认模块信息")
    void shouldCollectMethodPermissionsWithDefaultModuleInfo() {
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class))
                .thenReturn(Map.of("testController", new TestController()));

        listener.onApplicationEvent(mockEvent);

        Map<String, GXBasePermissionInnerDto> permissions = captureSinglePublishedPermissionMap("testController");
        assertEquals(3, permissions.size());

        GXBasePermissionInnerDto listPermission = permissions.get("user:list");
        assertEquals("User List", listPermission.getPermissionName());
        assertEquals("user", listPermission.getModuleCode());
        assertEquals("User Management", listPermission.getModuleName());

        GXBasePermissionInnerDto addPermission = permissions.get("system:user:add");
        assertEquals("Add User", addPermission.getPermissionName());
        assertEquals("system:user", addPermission.getModuleCode());
        assertEquals("System User", addPermission.getModuleName());
    }

    @Test
    @DisplayName("支持父类声明的权限方法")
    void shouldCollectPermissionMethodsFromSuperclass() {
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class))
                .thenReturn(Map.of("testController", new TestController()));

        listener.onApplicationEvent(mockEvent);

        Map<String, GXBasePermissionInnerDto> permissions = captureSinglePublishedPermissionMap("testController");
        assertTrue(permissions.containsKey("user:export"));
        assertFalse(permissions.containsKey("user:overridden"));
    }

    @Test
    @DisplayName("支持 AOP 代理 Bean 的目标类权限信息")
    void shouldCollectPermissionMethodsFromAopProxyTargetClass() {
        ProxyFactory proxyFactory = new ProxyFactory(new TestController());
        proxyFactory.setProxyTargetClass(true);
        Object proxy = proxyFactory.getProxy();
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class)).thenReturn(Map.of("proxyController", proxy));

        listener.onApplicationEvent(mockEvent);

        Map<String, GXBasePermissionInnerDto> permissions = captureSinglePublishedPermissionMap("proxyController");
        assertEquals(3, permissions.size());
        assertTrue(permissions.containsKey("user:list"));
        assertTrue(permissions.containsKey("user:export"));
    }

    @Test
    @DisplayName("支持接口声明的权限方法")
    void shouldCollectPermissionMethodsFromInterface() {
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class))
                .thenReturn(Map.of("interfaceController", new InterfacePermissionController()));

        listener.onApplicationEvent(mockEvent);

        Map<String, GXBasePermissionInnerDto> permissions = captureSinglePublishedPermissionMap("interfaceController");
        assertEquals(1, permissions.size());
        assertTrue(permissions.containsKey("interface:sync"));
        assertEquals("Interface", permissions.get("interface:sync").getModuleName());
    }

    @Test
    @DisplayName("单个 Bean 处理异常不会影响其他权限 Bean")
    void shouldContinueWhenOneBeanProcessingFails() {
        Map<String, Object> beans = new LinkedHashMap<>();
        beans.put("badBean", null);
        beans.put("testController", new TestController());
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class)).thenReturn(beans);

        listener.onApplicationEvent(mockEvent);

        Map<String, GXBasePermissionInnerDto> permissions = captureSinglePublishedPermissionMap("testController");
        assertEquals(3, permissions.size());
    }

    @Test
    @DisplayName("没有方法权限的 Bean 不发布事件")
    void shouldNotPublishEventWhenPermissionBeanHasNoPermissionMethods() {
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class))
                .thenReturn(Map.of("emptyController", new EmptyPermissionController()));

        listener.onApplicationEvent(mockEvent);

        mockedEventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), never());
    }

    @Test
    @DisplayName("方法扫描结果会被缓存并可在销毁时清理")
    void shouldCachePermissionMethodsAndClearCacheOnDestroy() {
        when(mockBeanFactory.getBeansWithAnnotation(GXPermissionCtl.class))
                .thenReturn(Map.of("testController", new TestController()));

        listener.onApplicationEvent(mockEvent);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Class<?>, List<Method>> cache =
                (ConcurrentHashMap<Class<?>, List<Method>>) ReflectionTestUtils.getField(listener, "permissionMethodCache");
        assertNotNull(cache);
        assertTrue(cache.containsKey(TestController.class));
        assertEquals(3, cache.get(TestController.class).size());

        listener.destroy();

        assertTrue(cache.isEmpty());
    }

    private Map<String, GXBasePermissionInnerDto> captureSinglePublishedPermissionMap(String beanName) {
        ArgumentCaptor<GXPermissionEvent> eventCaptor = ArgumentCaptor.forClass(GXPermissionEvent.class);
        mockedEventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(eventCaptor.capture()));
        Map<String, List<GXBasePermissionInnerDto>> permissionMap = eventCaptor.getValue().getSource();
        assertEquals(1, permissionMap.size());
        assertTrue(permissionMap.containsKey(beanName));
        return permissionMap.get(beanName).stream()
                .collect(Collectors.toMap(
                        GXBasePermissionInnerDto::getPermissionCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    interface InterfacePermissionApi {
        @GXPermission(permissionCode = "interface:sync", permissionName = "Interface Sync")
        void sync();
    }

    @GXPermissionCtl(moduleCode = "user", moduleName = "User Management")
    static class TestController extends BaseController {
        @GXPermission(permissionCode = "user:list", permissionName = "User List")
        public void list() {
        }

        @GXPermission(permissionCode = "system:user:add", permissionName = "Add User",
                moduleCode = "system:user", moduleName = "System User")
        public void add() {
        }

        @Override
        public void overridden() {
        }

        public void noPermission() {
        }
    }

    static class BaseController {
        @GXPermission(permissionCode = "user:export", permissionName = "Export User")
        public void export() {
        }

        @GXPermission(permissionCode = "user:overridden", permissionName = "Overridden Permission")
        public void overridden() {
        }
    }

    @GXPermissionCtl(moduleCode = "empty", moduleName = "Empty")
    static class EmptyPermissionController {
        public void noPermission() {
        }
    }

    @GXPermissionCtl(moduleCode = "interface", moduleName = "Interface")
    static class InterfacePermissionController implements InterfacePermissionApi {
        @Override
        public void sync() {
        }
    }
}
