package cn.maple.core.datasource.aspect.mybatis;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXMyBatisListenerDeduplicationTest {
    private final GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
    private final GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);

    @Test
    void differentListenersOnTwoInterfacesAreBothDelivered() throws Exception {
        Method method = AlphaMapper.class.getMethod("insert", Object.class);

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(resolver.mapperCandidateTypes(new DualMapperImpl()), method);

        assertEquals(2, listeners.size());
    }

    @Test
    void sameListenerDeclaredOnTwoInterfacesIsDeliveredOnce() throws Exception {
        Method method = GammaOneMapper.class.getMethod("insert", Object.class);

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(resolver.mapperCandidateTypes(new DualGammaMapperImpl()), method);

        assertEquals(1, listeners.size());
        assertEquals(AlphaListener.class, listeners.getFirst().listenerClazz());
    }

    @Test
    void parentInterfaceDeclarationIsResolvedOnce() throws Exception {
        Method method = ChildMapper.class.getMethod("insert", Object.class);

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(resolver.mapperCandidateTypes(new ChildMapperImpl()), method);

        assertEquals(1, listeners.size());
        assertEquals(ParentMapper.class.getName(), listeners.getFirst().declaredFrom(),
                "An inherited declaration must be attributed to the interface that declares it");
    }

    @Test
    void parentMethodDeclarationKeepsDeclaringInterface() throws Exception {
        Method method = ChildMethodMapper.class.getMethod("updateFieldByCondition", Object.class);

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(resolver.mapperCandidateTypes(new ChildMethodMapperImpl()), method);

        assertEquals(1, listeners.size());
        assertEquals(BetaListener.class, listeners.getFirst().listenerClazz());
        assertEquals(MethodLevelParentMapper.class.getName() + "#updateFieldByCondition",
                listeners.getFirst().declaredFrom());
    }

    @Test
    void methodLevelDeclarationWinsOverTypeLevelDeclaration() throws Exception {
        Method method = MethodLevelMapper.class.getMethod("updateFieldByCondition", Object.class);

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(resolver.mapperCandidateTypes(new MethodLevelMapperImpl()), method);

        assertEquals(1, listeners.size());
        assertEquals(BetaListener.class, listeners.getFirst().listenerClazz());
    }

    @Test
    void deduplicateSwitchCanRestoreLegacyMultiDelivery() throws Exception {
        properties.setDeduplicate(false);
        GXMyBatisListenerConfigResolver legacyResolver = new GXMyBatisListenerConfigResolver(properties);
        Method method = GammaOneMapper.class.getMethod("insert", Object.class);

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                legacyResolver.resolve(legacyResolver.mapperCandidateTypes(new DualGammaMapperImpl()), method);

        assertEquals(2, listeners.size());
    }

    @Test
    void unannotatedMapperResolvesToEmptyListWithoutThrowing() throws Exception {
        Method method = PlainMapper.class.getMethod("insert", Object.class);

        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(resolver.mapperCandidateTypes(new PlainMapperImpl()), method);

        assertEquals(List.of(), listeners);
    }

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface AlphaMapper {
        Object insert(Object entity);
    }

    @GXMyBatisListener(listenerClazz = BetaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface BetaMapper {
        Object insert(Object entity);
    }

    private static class DualMapperImpl implements AlphaMapper, BetaMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface GammaOneMapper {
        Object insert(Object entity);
    }

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface GammaTwoMapper {
        Object insert(Object entity);
    }

    private static class DualGammaMapperImpl implements GammaOneMapper, GammaTwoMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface ParentMapper {
        Object insert(Object entity);
    }

    private interface ChildMapper extends ParentMapper {
    }

    private static class ChildMapperImpl implements ChildMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    @GXMyBatisListener(listenerClazz = BetaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface MethodLevelParentMapper {
        @GXMyBatisListener(listenerClazz = BetaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
        Object updateFieldByCondition(Object query);
    }

    private interface ChildMethodMapper extends MethodLevelParentMapper {
    }

    private static class ChildMethodMapperImpl implements ChildMethodMapper {
        @Override
        public Object updateFieldByCondition(Object query) {
            return 1;
        }
    }

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface MethodLevelMapper {
        @GXMyBatisListener(listenerClazz = BetaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
        Object updateFieldByCondition(Object query);
    }

    private static class MethodLevelMapperImpl implements MethodLevelMapper {
        @Override
        public Object updateFieldByCondition(Object query) {
            return 1;
        }
    }

    private interface PlainMapper {
        Object insert(Object entity);
    }

    private static class PlainMapperImpl implements PlainMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class AlphaListener implements GXMybatisListenerService<Object> {
    }

    private static class BetaListener implements GXMybatisListenerService<Object> {
    }
}
