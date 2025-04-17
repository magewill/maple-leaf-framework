package cn.maple.core.datasource.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.service.GXMyBatisAutoFillMetaObjectService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis公共字段自动填充器
 * 用于在插入和更新操作时自动填充创建和更新相关字段
 * 该类是线程安全的，因为它不维护任何状态，每次操作都是基于传入的MetaObject对象
 *
 * @author britton <britton@126.com>
 */
@Slf4j
@Component
public class GXAutoFillMetaObjectHandler implements MetaObjectHandler {
    /**
     * 插入操作时自动填充字段
     * 主要填充createdBy和createdAt字段
     * 如果字段已有值则不会覆盖
     *
     * @param metaObject Mybatis元数据对象，不应为null
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 安全获取createdBy字段值，避免类型转换异常
        String createdBy = (String) metaObject.getValue("createdBy");
        if (CharSequenceUtil.isEmpty(createdBy)) {
            createdBy = "unknown";
            // 从Spring上下文中安全获取服务实例，用于获取当前操作用户
            GXMyBatisAutoFillMetaObjectService myBatisAutoFillMetaObjectService = GXSpringContextUtils.getBean(GXMyBatisAutoFillMetaObjectService.class);
            if (Objects.nonNull(myBatisAutoFillMetaObjectService)) {
                createdBy = myBatisAutoFillMetaObjectService.getCreatedBy();
            }
            this.setFieldValByName("createdBy", createdBy, metaObject);
        }
        // 安全获取createdAt字段值，使用Optional避免空指针异常
        Integer createdAt = (Integer) Optional.ofNullable(metaObject.getValue("createdAt")).orElse(0);
        if (createdAt.equals(0)) {
            // 使用Math.toIntExact安全地将long转为int，避免溢出风险
            final Integer timestamp = Math.toIntExact(DateUtil.currentSeconds());
            this.setFieldValByName("createdAt", timestamp, metaObject);
        }
    }

    /**
     * 更新操作时自动填充字段
     * 主要填充updatedBy和updatedAt字段
     * 如果字段已有值则不会覆盖
     *
     * @param metaObject Mybatis元数据对象，不应为null
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 安全获取updatedBy字段值，避免类型转换异常
        String updatedBy = (String) metaObject.getValue("updatedBy");
        if (CharSequenceUtil.isEmpty(updatedBy)) {
            updatedBy = "unknown";
            // 从Spring上下文中安全获取服务实例，用于获取当前操作用户
            GXMyBatisAutoFillMetaObjectService myBatisAutoFillMetaObjectService = GXSpringContextUtils.getBean(GXMyBatisAutoFillMetaObjectService.class);
            if (Objects.nonNull(myBatisAutoFillMetaObjectService)) {
                updatedBy = myBatisAutoFillMetaObjectService.getUpdatedBy();
            }
            this.setFieldValByName("updatedBy", updatedBy, metaObject);
        }
        // 安全获取updatedAt字段值，使用Optional避免空指针异常
        Integer updatedAt = (Integer) Optional.ofNullable(metaObject.getValue("updatedAt")).orElse(0);
        if (updatedAt.equals(0)) {
            // 使用Math.toIntExact安全地将long转为int，避免溢出风险
            final Integer timestamp = Math.toIntExact(DateUtil.currentSeconds());
            this.setFieldValByName("updatedAt", timestamp, metaObject);
        }
    }
}
