package cn.maple.core.datasource.service;

import cn.hutool.core.lang.Dict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface GXMybatisListenerService<T> {
    Logger LOG = LoggerFactory.getLogger(GXMybatisListenerService.class);

    default void saveEntityListener(T data) {
        LOG.info("请自定义实现saveEntityListener监听逻辑");
    }

    default void updateEntityListener(T data, Dict keyValuePairs, Dict keyOperatorPairs) {
        LOG.info("请自定义实现updateEntityListener监听逻辑");
    }

    default void updateFieldListener(Dict updateFieldData, Dict conditionFieldData) {
        LOG.info("请自定义实现updateFieldListener监听逻辑");
    }

    default void deleteSoftListener(Dict data) {
        LOG.info("请自定义实现deleteSoftListener监听逻辑");
    }

    default void deleteListener(Dict data) {
        LOG.info("Physical delete listener invoked");
    }

    default void saveBatchListener(Dict data) {
        LOG.info("请自定义实现saveBatchListener监听逻辑");
    }

    default void batchChangeListener(Dict data) {
        saveBatchListener(data);
    }
}
