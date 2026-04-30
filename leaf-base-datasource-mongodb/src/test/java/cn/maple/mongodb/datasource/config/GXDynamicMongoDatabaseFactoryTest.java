package cn.maple.mongodb.datasource.config;

import cn.maple.mongodb.datasource.context.GXMongoTemplateContext;
import cn.maple.core.framework.exception.GXBusinessException;
import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDynamicMongoDatabaseFactoryTest {
    @AfterEach
    void tearDown() {
        GXMongoTemplateContext.clear();
    }

    @Test
    void sessionBoundFactoryShouldKeepDynamicRouting() {
        FakeMongoDatabaseFactory primaryFactory = new FakeMongoDatabaseFactory();
        FakeMongoDatabaseFactory secondaryFactory = new FakeMongoDatabaseFactory();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("primaryFactory", primaryFactory);
        beanFactory.addBean("secondaryFactory", secondaryFactory);

        GXDynamicMongoDatabaseFactory dynamicFactory = new GXDynamicMongoDatabaseFactory(
                "primaryFactory",
                Map.of("secondaryMongoTemplate", "secondaryFactory"));
        dynamicFactory.setBeanFactory(beanFactory);
        ClientSession session = clientSession();

        GXMongoTemplateContext.push("secondaryMongoTemplate");
        MongoDatabaseFactory sessionBoundFactory = dynamicFactory.withSession(session);
        assertTrue(sessionBoundFactory instanceof GXDynamicMongoDatabaseFactory);

        sessionBoundFactory.getExceptionTranslator();

        assertSame(session, secondaryFactory.lastSession);
    }

    @Test
    void sessionBoundFactoryShouldRejectSwitchingToDifferentDatasource() {
        FakeMongoDatabaseFactory primaryFactory = new FakeMongoDatabaseFactory();
        FakeMongoDatabaseFactory secondaryFactory = new FakeMongoDatabaseFactory();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("primaryFactory", primaryFactory);
        beanFactory.addBean("secondaryFactory", secondaryFactory);

        GXDynamicMongoDatabaseFactory dynamicFactory = new GXDynamicMongoDatabaseFactory(
                "primaryFactory",
                Map.of("secondaryMongoTemplate", "secondaryFactory"));
        dynamicFactory.setBeanFactory(beanFactory);

        ClientSession session = dynamicFactory.getSession(ClientSessionOptions.builder().build());
        MongoDatabaseFactory sessionBoundFactory = dynamicFactory.withSession(session);

        GXMongoTemplateContext.push("secondaryMongoTemplate");

        assertThrows(GXBusinessException.class, sessionBoundFactory::getExceptionTranslator);
    }

    private ClientSession clientSession() {
        return (ClientSession) Proxy.newProxyInstance(
                ClientSession.class.getClassLoader(),
                new Class<?>[]{ClientSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static class FakeMongoDatabaseFactory implements MongoDatabaseFactory {
        private ClientSession lastSession;

        @Override
        public MongoDatabase getMongoDatabase() throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MongoDatabase getMongoDatabase(String dbName) throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public PersistenceExceptionTranslator getExceptionTranslator() {
            return ex -> null;
        }

        @Override
        public CodecRegistry getCodecRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientSession getSession(ClientSessionOptions options) {
            return clientSession();
        }

        @Override
        public MongoDatabaseFactory withSession(ClientSessionOptions options) {
            return withSession(getSession(options));
        }

        @Override
        public MongoDatabaseFactory withSession(ClientSession session) {
            FakeMongoDatabaseFactory sessionBoundFactory = new FakeMongoDatabaseFactory();
            sessionBoundFactory.lastSession = session;
            this.lastSession = session;
            return sessionBoundFactory;
        }

        @Override
        public boolean isTransactionActive() {
            return false;
        }

        private ClientSession clientSession() {
            return (ClientSession) Proxy.newProxyInstance(
                    ClientSession.class.getClassLoader(),
                    new Class<?>[]{ClientSession.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
        }
    }
}
