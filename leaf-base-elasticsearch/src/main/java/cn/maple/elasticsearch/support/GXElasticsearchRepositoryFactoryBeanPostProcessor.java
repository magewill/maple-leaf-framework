package cn.maple.elasticsearch.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.support.ElasticsearchRepositoryFactoryBean;

/**
 * Ensures Spring Data repository proxy methods use the same dynamic operations
 * dispatcher as the framework default methods.
 */
public class GXElasticsearchRepositoryFactoryBeanPostProcessor implements BeanPostProcessor {
    private final ElasticsearchOperations elasticsearchOperations;

    public GXElasticsearchRepositoryFactoryBeanPostProcessor(String defaultTemplateBeanName) {
        this.elasticsearchOperations = GXDynamicElasticsearchOperations.create(defaultTemplateBeanName);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ElasticsearchRepositoryFactoryBean<?, ?, ?> factoryBean) {
            factoryBean.setElasticsearchOperations(elasticsearchOperations);
        }
        return bean;
    }
}
