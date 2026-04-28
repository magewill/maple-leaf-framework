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

import java.util.Map;

public class GXDynamicMongoDatabaseFactory implements MongoDatabaseFactory, BeanFactoryAware {
    private final String defaultFactoryBeanName;

    private final Map<String, String> templateFactoryBeanNames;

    private BeanFactory beanFactory;

    public GXDynamicMongoDatabaseFactory(String defaultFactoryBeanName, Map<String, String> templateFactoryBeanNames) {
        this.defaultFactoryBeanName = defaultFactoryBeanName;
        this.templateFactoryBeanNames = templateFactoryBeanNames;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public MongoDatabase getMongoDatabase() throws DataAccessException {
        return determineTargetFactory().getMongoDatabase();
    }

    @Override
    public MongoDatabase getMongoDatabase(String dbName) throws DataAccessException {
        return determineTargetFactory().getMongoDatabase(dbName);
    }

    @Override
    public PersistenceExceptionTranslator getExceptionTranslator() {
        return determineTargetFactory().getExceptionTranslator();
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return determineTargetFactory().getCodecRegistry();
    }

    @Override
    public ClientSession getSession(ClientSessionOptions options) {
        return determineTargetFactory().getSession(options);
    }

    @Override
    public MongoDatabaseFactory withSession(ClientSessionOptions options) {
        return determineTargetFactory().withSession(options);
    }

    @Override
    public MongoDatabaseFactory withSession(ClientSession session) {
        return determineTargetFactory().withSession(session);
    }

    @Override
    public boolean isTransactionActive() {
        return determineTargetFactory().isTransactionActive();
    }

    private MongoDatabaseFactory determineTargetFactory() {
        String templateBeanName = GXMongoTemplateContext.peek();
        String factoryBeanName = CharSequenceUtil.isBlank(templateBeanName)
                ? defaultFactoryBeanName
                : templateFactoryBeanNames.get(templateBeanName);
        if (CharSequenceUtil.isBlank(factoryBeanName)) {
            throw new GXBusinessException("MongoTemplate bean [" + templateBeanName + "] does not exist");
        }
        return beanFactory.getBean(factoryBeanName, MongoDatabaseFactory.class);
    }
}
