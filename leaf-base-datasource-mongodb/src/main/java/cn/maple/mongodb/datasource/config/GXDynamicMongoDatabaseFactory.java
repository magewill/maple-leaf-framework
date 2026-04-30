package cn.maple.mongodb.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.mongodb.datasource.context.GXMongoTemplateContext;
import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public class GXDynamicMongoDatabaseFactory implements MongoDatabaseFactory, BeanFactoryAware {
    private static final Map<ClientSession, String> SESSION_FACTORY_BEAN_NAMES = new WeakHashMap<>();

    private final String defaultFactoryBeanName;

    private final Map<String, String> templateFactoryBeanNames;

    private final ClientSession session;

    private final String sessionFactoryBeanName;

    private BeanFactory beanFactory;

    public GXDynamicMongoDatabaseFactory(String defaultFactoryBeanName, Map<String, String> templateFactoryBeanNames) {
        this(defaultFactoryBeanName, templateFactoryBeanNames, null, null);
    }

    private GXDynamicMongoDatabaseFactory(String defaultFactoryBeanName,
                                          Map<String, String> templateFactoryBeanNames,
                                          ClientSession session,
                                          String sessionFactoryBeanName) {
        this.defaultFactoryBeanName = defaultFactoryBeanName;
        this.templateFactoryBeanNames = new LinkedHashMap<>(templateFactoryBeanNames);
        this.session = session;
        this.sessionFactoryBeanName = sessionFactoryBeanName;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public MongoDatabase getMongoDatabase() throws DataAccessException {
        return determineTargetFactory().mongoDatabaseFactory().getMongoDatabase();
    }

    @Override
    public MongoDatabase getMongoDatabase(String dbName) throws DataAccessException {
        return determineTargetFactory().mongoDatabaseFactory().getMongoDatabase(dbName);
    }

    @Override
    public PersistenceExceptionTranslator getExceptionTranslator() {
        return determineTargetFactory().mongoDatabaseFactory().getExceptionTranslator();
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return determineTargetFactory().mongoDatabaseFactory().getCodecRegistry();
    }

    @Override
    public ClientSession getSession(ClientSessionOptions options) {
        TargetMongoDatabaseFactory target = determineTargetFactory();
        ClientSession clientSession = target.mongoDatabaseFactory().getSession(options);
        rememberSessionFactoryBeanName(clientSession, target.factoryBeanName());
        return clientSession;
    }

    @Override
    public MongoDatabaseFactory withSession(ClientSessionOptions options) {
        return withSession(getSession(options));
    }

    @Override
    public MongoDatabaseFactory withSession(ClientSession session) {
        TargetMongoDatabaseFactory target = determineTargetFactory();
        String factoryBeanName = resolveSessionFactoryBeanName(session, target.factoryBeanName());
        GXDynamicMongoDatabaseFactory sessionBoundFactory =
                new GXDynamicMongoDatabaseFactory(defaultFactoryBeanName, templateFactoryBeanNames, session, factoryBeanName);
        sessionBoundFactory.setBeanFactory(beanFactory);
        return sessionBoundFactory;
    }

    @Override
    public boolean isTransactionActive() {
        return determineTargetFactory().mongoDatabaseFactory().isTransactionActive();
    }

    private TargetMongoDatabaseFactory determineTargetFactory() {
        if (Objects.isNull(beanFactory)) {
            throw new GXBusinessException("BeanFactory has not been initialized");
        }
        String templateBeanName = GXMongoTemplateContext.peek();
        String factoryBeanName = CharSequenceUtil.isBlank(templateBeanName)
                ? defaultFactoryBeanName
                : templateFactoryBeanNames.get(templateBeanName);
        if (CharSequenceUtil.isBlank(factoryBeanName)) {
            throw new GXBusinessException("MongoTemplate bean [" + templateBeanName + "] does not exist");
        }
        if (session != null && CharSequenceUtil.isNotBlank(sessionFactoryBeanName)
                && !Objects.equals(sessionFactoryBeanName, factoryBeanName)) {
            throw new GXBusinessException("MongoDB ClientSession belongs to datasource [" + sessionFactoryBeanName
                    + "], cannot switch to datasource [" + factoryBeanName + "] in the same session");
        }
        MongoDatabaseFactory targetFactory = beanFactory.getBean(factoryBeanName, MongoDatabaseFactory.class);
        MongoDatabaseFactory sessionAwareTargetFactory = Objects.isNull(session) ? targetFactory : targetFactory.withSession(session);
        return new TargetMongoDatabaseFactory(factoryBeanName, sessionAwareTargetFactory);
    }

    private void rememberSessionFactoryBeanName(ClientSession session, String factoryBeanName) {
        if (session == null || CharSequenceUtil.isBlank(factoryBeanName)) {
            return;
        }
        synchronized (SESSION_FACTORY_BEAN_NAMES) {
            SESSION_FACTORY_BEAN_NAMES.put(session, factoryBeanName);
        }
    }

    private String resolveSessionFactoryBeanName(ClientSession session, String fallbackFactoryBeanName) {
        if (session == null) {
            return fallbackFactoryBeanName;
        }
        synchronized (SESSION_FACTORY_BEAN_NAMES) {
            return SESSION_FACTORY_BEAN_NAMES.computeIfAbsent(session, ignored -> fallbackFactoryBeanName);
        }
    }

    private record TargetMongoDatabaseFactory(String factoryBeanName, MongoDatabaseFactory mongoDatabaseFactory) {
    }
}
