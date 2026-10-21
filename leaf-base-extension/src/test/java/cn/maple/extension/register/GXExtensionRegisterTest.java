package cn.maple.extension.register;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionCoordinate;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.GXExtensions;
import cn.maple.extension.exception.GXExtensionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void registrationExtensionsRegistersEveryCartesianCoordinate() {
        extensionRegister.doRegistrationExtensions(new CartesianExtension());

        assertEquals(8, extensionRepository.size());
        assertCartesianCoordinate("cartA", "useCaseA", "scenarioA");
        assertCartesianCoordinate("cartA", "useCaseA", "scenarioB");
        assertCartesianCoordinate("cartA", "useCaseB", "scenarioA");
        assertCartesianCoordinate("cartA", "useCaseB", "scenarioB");
        assertCartesianCoordinate("cartB", "useCaseA", "scenarioA");
        assertCartesianCoordinate("cartB", "useCaseA", "scenarioB");
        assertCartesianCoordinate("cartB", "useCaseB", "scenarioA");
        assertCartesianCoordinate("cartB", "useCaseB", "scenarioB");
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

    @Test
    void composedAnnotationWithAliasForIsMergedCorrectly() {
        extensionRegister.doRegistration(new ComposedAliasExtension());

        assertSame(ComposedAliasExtension.class,
                extensionRepository.findExtension(coordinate(GXBizScenario.valueOf("aliasBiz"))).orElseThrow().getClass());
    }

    @Test
    void singleEntryRegistrationRegistersRepeatedCoordinates() {
        extensionRegister.doRegistration(new RepeatedAnnotationExtension());

        assertEquals(2, extensionRepository.size());
        assertSame(RepeatedAnnotationExtension.class,
                extensionRepository.findExtension(coordinate(GXBizScenario.valueOf("repeatA"))).orElseThrow().getClass());
        assertSame(RepeatedAnnotationExtension.class,
                extensionRepository.findExtension(coordinate(GXBizScenario.valueOf("repeatB"))).orElseThrow().getClass());
    }

    @Test
    void singleEntryRegistrationRegistersExplicitContainerCoordinates() {
        extensionRegister.doRegistration(new MultiValueExtension());

        assertEquals(2, extensionRepository.size());
        assertRegistered(GXBizScenario.valueOf("bizA", "useCaseA", "scenarioA"));
        assertRegistered(GXBizScenario.valueOf("bizB", "useCaseB", "scenarioB"));
    }

    @Test
    void singleEntryRegistrationRegistersCartesianProduct() {
        extensionRegister.doRegistration(new CartesianExtension());

        assertEquals(8, extensionRepository.size());
        assertCartesianCoordinate("cartA", "useCaseA", "scenarioA");
        assertCartesianCoordinate("cartB", "useCaseB", "scenarioB");
    }

    @Test
    void singleEntryRegistrationRegistersMixedDeclarations() {
        extensionRegister.doRegistration(new MixedDeclarationExtension());

        assertEquals(2, extensionRepository.size());
        assertSame(MixedDeclarationExtension.class, extensionRepository
                .findExtension(coordinate(GXBizScenario.valueOf("mixedA"))).orElseThrow().getClass());
        assertSame(MixedDeclarationExtension.class, extensionRepository
                .findExtension(coordinate(GXBizScenario.valueOf("mixedB"))).orElseThrow().getClass());
    }

    @Test
    void emptyContainerDeclarationFailsFastWithDeclarationContext() {
        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new EmptyContainerExtension()));

        assertTrue(failure.getMessage().contains(EmptyContainerExtension.class.getName()));
        assertTrue(failure.getMessage().contains("GXExtensions"));
        assertEquals(0, extensionRepository.size());
    }

    @Test
    void parentAndChildDeclaringSameCoordinateFailFast() {
        extensionRegister.doRegistration(new ParentExtension());

        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new ChildExtension()));

        assertTrue(failure.getMessage().contains(ParentExtension.class.getName()));
        assertTrue(failure.getMessage().contains(ChildExtension.class.getName()));
    }

    @Test
    void blankSingleAnnotationAttributeFailsWithImplementationContext() {
        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new BlankSingleAttributeExtension()));

        assertTrue(failure.getMessage().contains(BlankSingleAttributeExtension.class.getName()));
        assertTrue(failure.getMessage().contains("GXExtension"));
        assertTrue(failure.getMessage().contains("bizId"));
        assertTrue(isAscii(failure.getMessage()));
        assertEquals(0, extensionRepository.size());
    }

    @Test
    void blankContainerAttributeFailsWithImplementationContext() {
        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new BlankCartesianAttributeExtension()));

        assertTrue(failure.getMessage().contains(BlankCartesianAttributeExtension.class.getName()));
        assertTrue(failure.getMessage().contains("GXExtensions(bizId="));
        assertTrue(failure.getMessage().contains("bizId"));
        assertTrue(isAscii(failure.getMessage()));
        assertEquals(0, extensionRepository.size());
    }

    @Test
    void nonAsciiAttributeValueIsEscapedInFailureMessage() {
        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new NonAsciiAttributeExtension()));

        assertTrue(failure.getMessage().contains(NonAsciiAttributeExtension.class.getName()));
        assertTrue(failure.getMessage().contains("\\u"));
        assertTrue(isAscii(failure.getMessage()));
    }

    @Test
    void duplicateRegistrationMessageCarriesCoordinateAndBothImplementations() {
        extensionRegister.doRegistration(new FirstDuplicateExtension());

        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new SecondDuplicateExtension()));

        assertTrue(failure.getMessage().contains(GXBizScenario.valueOf("dup").getUniqueIdentity()));
        assertTrue(failure.getMessage().contains(FirstDuplicateExtension.class.getName()));
        assertTrue(failure.getMessage().contains(SecondDuplicateExtension.class.getName()));
        assertTrue(isAscii(failure.getMessage()));
    }

    @Test
    void multipleExtensionPointInterfacesMessageListsAllCandidates() {
        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new MultiExtPointExtension()));

        assertTrue(failure.getMessage().contains(MultiExtPointExtension.class.getName()));
        assertTrue(failure.getMessage().contains(RegisterTestExtPoint.class.getName()));
        assertTrue(failure.getMessage().contains(AnotherRegisterTestExtPoint.class.getName()));
        assertTrue(isAscii(failure.getMessage()));
    }

    @Test
    void namingViolationMessageCarriesImplementationAndConvention() {
        GXExtensionException failure = assertThrows(GXExtensionException.class,
                () -> extensionRegister.doRegistration(new MarkerOnlyExtension()));

        assertTrue(failure.getMessage().contains(MarkerOnlyExtension.class.getName()));
        assertTrue(failure.getMessage().contains(GXExtensionRegister.EXTENSION_EXT_PT_NAMING));
        assertTrue(isAscii(failure.getMessage()));
    }

    private boolean isAscii(String text) {
        return text.chars().allMatch(character -> character < 128);
    }

    private void assertRegistered(GXBizScenario bizScenario) {
        assertEquals(MultiValueExtension.class,
                extensionRepository.findExtension(coordinate(bizScenario)).orElseThrow().getClass());
    }

    private void assertCartesianCoordinate(String bizId, String useCase, String scenario) {
        assertEquals(CartesianExtension.class,
                extensionRepository.findExtension(coordinate(GXBizScenario.valueOf(bizId, useCase, scenario)))
                        .orElseThrow()
                        .getClass());
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

    @GXExtensions(
            bizId = {"cartA", "cartB"},
            useCase = {"useCaseA", "useCaseB"},
            scenario = {"scenarioA", "scenarioB"}
    )
    private static class CartesianExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "dup")
    private static class FirstDuplicateExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "dup")
    private static class SecondDuplicateExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "repeatA")
    @GXExtension(bizId = "repeatB")
    private static class RepeatedAnnotationExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "mixedA")
    @GXExtensions({@GXExtension(bizId = "mixedB")})
    private static class MixedDeclarationExtension implements RegisterTestExtPoint {
    }

    @GXExtensions(bizId = {})
    private static class EmptyContainerExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = " ")
    private static class BlankSingleAttributeExtension implements RegisterTestExtPoint {
    }

    @GXExtensions(bizId = {" "})
    private static class BlankCartesianAttributeExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "订单.明细")
    private static class NonAsciiAttributeExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "inherit")
    private static class ParentExtension implements RegisterTestExtPoint {
    }

    @GXExtension(bizId = "inherit")
    private static class ChildExtension extends ParentExtension {
    }

    @GXExtension(bizId = "marker")
    private static class MarkerOnlyExtension implements GXExtensionPoint {
    }

    @GXExtension(bizId = "multi")
    private static class MultiExtPointExtension implements RegisterTestExtPoint, AnotherRegisterTestExtPoint {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @GXExtension
    private @interface AliasGXExtension {
        @AliasFor(annotation = GXExtension.class, attribute = "bizId")
        String bizId() default GXBizScenario.DEFAULT_BIZ_ID;
    }

    @AliasGXExtension(bizId = "aliasBiz")
    private static class ComposedAliasExtension implements RegisterTestExtPoint {
    }
}
