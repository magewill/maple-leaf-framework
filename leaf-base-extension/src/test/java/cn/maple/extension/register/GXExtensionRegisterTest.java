package cn.maple.extension.register;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionCoordinate;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.GXExtensions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXExtensionRegisterTest {
    private GXExtensionRepository extensionRepository;
    private GXExtensionRegister extensionRegister;

    @BeforeEach
    void setUp() {
        extensionRepository = new GXExtensionRepository();
        extensionRegister = new GXExtensionRegister();
        ReflectionTestUtils.setField(extensionRegister, "extensionRepository", extensionRepository);
    }

    @Test
    void registrationExtensionsWithValueDoesNotRegisterDefaultCartesianCoordinate() {
        extensionRegister.doRegistrationExtensions(new MultiValueExtension());

        assertEquals(2, extensionRepository.getExtensionRepo().size());
        assertRegistered(GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"));
        assertRegistered(GXBizScenario.valueOf("bizB", "useCaseB", "scenarioB"));
        assertFalse(extensionRepository.findExtension(coordinate(GXBizScenario.newDefault())).isPresent());
    }

    @Test
    void duplicateRegistrationFailsFast() {
        FirstDuplicateExtension first = new FirstDuplicateExtension();

        extensionRegister.doRegistration(first);

        assertThrows(GXBusinessException.class, () -> extensionRegister.doRegistration(new SecondDuplicateExtension()));
        assertSame(first, extensionRepository.findExtension(coordinate(GXBizScenario.valueOf("dup"))).orElseThrow());
    }

    @Test
    void directMarkerInterfaceIsNotTreatedAsExtensionPoint() {
        assertThrows(GXBusinessException.class, () -> extensionRegister.doRegistration(new MarkerOnlyExtension()));
    }

    @Test
    void multipleExtensionPointInterfacesFailFast() {
        assertThrows(GXBusinessException.class, () -> extensionRegister.doRegistration(new MultiExtPointExtension()));
    }

    private void assertRegistered(GXBizScenario bizScenario) {
        assertEquals(MultiValueExtension.class,
                extensionRepository.findExtension(coordinate(bizScenario)).orElseThrow().getClass());
    }

    private GXExtensionCoordinate coordinate(GXBizScenario bizScenario) {
        return new GXExtensionCoordinate(RegisterTestExtPoint.class, bizScenario);
    }

    private interface RegisterTestExtPoint extends GXExtensionPoint {
    }

    private interface AnotherRegisterTestExtPoint extends GXExtensionPoint {
    }

    @GXExtensions(value = {
            @GXExtension(bizId = "bizA", useCase = "useCaseA", scenario = "scenarioA"),
            @GXExtension(bizId = "bizB", useCase = "useCaseB", scenario = "scenarioB")
    })
    private static class MultiValueExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "dup")
    private static class FirstDuplicateExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "dup")
    private static class SecondDuplicateExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "marker")
    private static class MarkerOnlyExtension implements GXExtensionPoint {
    }

    @GXExtension(bizId = "multi")
    private static class MultiExtPointExtension implements RegisterTestExtPoint, AnotherRegisterTestExtPoint {
    }
}
