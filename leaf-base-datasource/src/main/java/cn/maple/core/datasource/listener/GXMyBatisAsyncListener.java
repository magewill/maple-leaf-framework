package cn.maple.core.datasource.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.event.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * MyBatis异步事件监听器
 * <p>
 * 该监听器用于异步处理MyBatis操作产生的事件，包括实体保存、更新、字段更新、软删除和批量保存等操作。
 * 所有事件处理方法都在单独的线程池中执行，不会阻塞调用线程。使用{@code @Async}注解和指定的线程池
 * {@code myBatisEventAsyncTaskExecutor}来实现异步处理。
 * <p>
 * 线程安全说明：
 * 1. 该监听器在异步模式下工作，所有事件处理都在独立的线程中执行
 * 2. 事件处理过程中的异常不会影响调用方，但需要在监听器内部正确处理和记录异常
 * 3. 由于使用线程池，需要注意避免线程池耗尽的情况，合理配置线程池参数
 * 4. 异步处理中使用的共享资源需要确保线程安全
 * <p>
 * 使用场景：
 * 1. 与主业务流程关联度不高的操作，如日志记录、统计分析等
 * 2. 可以容忍一定延迟的操作
 * 3. 需要提高系统吞吐量，不希望事件处理阻塞主流程的场景
 * <p>
 * 示例：当保存用户实体后，异步发送欢迎邮件或进行数据统计
 */
@Component
@Log4j2
@SuppressWarnings("all")
@Async(value = "myBatisEventAsyncTaskExecutor")
public class GXMyBatisAsyncListener implements GXMyBatisBaseListener {
    /**
     * 监听保存实体(Entity)事件
     *
     * @param saveEntityEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).ASYNC_SAVE_ENTITY.eventType)")
    public void listenerSaveEntity(GXMyBatisModelSaveEntityEvent<Dict> saveEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveEntity(saveEntityEvent);
    }

    /**
     * 监听更新实体(Entity)事件
     *
     * @param updateEntityEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).ASYNC_UPDATE_ENTITY.eventType)")
    public void listenerUpdateEntity(GXMyBatisModelUpdateEntityEvent<Dict> updateEntityEvent) {
        GXMyBatisBaseListener.super.listenerUpdateEntity(updateEntityEvent);
    }

    /**
     * 监听更新指定字段事件
     *
     * @param updateFieldEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).ASYNC_UPDATE_FIELD.eventType)")
    public void listenerUpdateField(GXMyBatisModelUpdateFieldEvent<Dict> updateFieldEvent) {
        GXMyBatisBaseListener.super.listenerUpdateField(updateFieldEvent);
    }

    /**
     * 监听更新指定字段事件
     *
     * @param deleteSoftEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).ASYNC_DELETE_SOFT.eventType)")
    public void listenerDeleteSoft(GXMyBatisModelDeleteSoftEvent<Dict> deleteSoftEvent) {
        GXMyBatisBaseListener.super.listenerDeleteSoft(deleteSoftEvent);
    }

    /**
     * 监听批量新增与更新事件
     *
     * @param saveBatchEntityEvent 事件对象
     */
    @EventListener(condition = "#root.event.eventType.equals(T(cn.maple.core.datasource.enums.GXModelEventNamingEnums).ASYNC_SAVE_BATCH_ENTITY.eventType)")
    public void listenerSaveBatch(GXMyBatisModelSaveBatchEntityEvent<Dict> saveBatchEntityEvent) {
        GXMyBatisBaseListener.super.listenerSaveBatch(saveBatchEntityEvent);
    }
}
