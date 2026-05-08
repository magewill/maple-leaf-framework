package cn.maple.extension;

import cn.maple.extension.register.*;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ExtTestApplication.class)
public class ExtensionRegisterTest {
    @Resource
    private GXExtensionRegister register;

    @Resource
    private GXExtensionExecutor executor;

    @Test
    public void test() {
        GXSomeExtPoint proxyExtension = CglibProxyFactory.createProxy(new ManualProxyExtension());
        register.doRegistration(proxyExtension);

        executor.executeVoid(GXSomeExtPoint.class, GXBizScenario.valueOf("A"), GXSomeExtPoint::doSomeThing);
        executor.executeVoid(GXSomeExtPoint.class, GXBizScenario.valueOf("B"), GXSomeExtPoint::doSomeThing);
        executor.executeVoid(GXSomeExtPoint.class, GXBizScenario.valueOf("manualProxy"), GXSomeExtPoint::doSomeThing);
    }

    @GXExtension(bizId = "manualProxy")
    public static class ManualProxyExtension implements GXSomeExtPoint {
        @Override
        public void doSomeThing() {
            System.out.println("ManualProxyExtension::doSomeThing");
        }
    }
}
