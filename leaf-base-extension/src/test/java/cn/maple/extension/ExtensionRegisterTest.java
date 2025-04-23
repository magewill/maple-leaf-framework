package cn.maple.extension;

import cn.maple.extension.register.*;
import jakarta.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ExtTestApplication.class)
public class ExtensionRegisterTest {
    @Resource
    private GXExtensionRegister register;

    @Resource
    private GXExtensionExecutor executor;

    @Test
    public void test() {
        GXSomeExtPoint extA = new SomeExtensionA();
        register.doRegistration(extA);

        GXSomeExtPoint extB = CglibProxyFactory.createProxy(new SomeExtensionB());
        register.doRegistration(extB);

        executor.executeVoid(GXSomeExtPoint.class, GXBizScenario.valueOf("A"), GXSomeExtPoint::doSomeThing);
        executor.executeVoid(GXSomeExtPoint.class, GXBizScenario.valueOf("B"), GXSomeExtPoint::doSomeThing);
    }
}
