package cn.maple.core.datasource.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.Objects;

/**
 * 多租户ID服务接口
 * <p>
 * 该接口用于在多租户系统中获取当前租户的ID，支持数据隔离。
 * 实现该接口可以自定义租户ID的获取逻辑，例如从请求头、线程上下文或其他来源获取。
 * 通过MyBatis-Plus的多租户插件，系统会自动为SQL添加租户条件，确保查询只返回当前租户的数据。
 * </p>
 *
 * <p>
 * 主要功能：
 * - 提供租户ID的获取接口，支持自定义租户ID来源
 * - 支持表级别的租户过滤控制，可以指定哪些表不需要进行租户过滤
 * - 使用缓存优化表结构检查，提高性能
 * - 与MyBatis-Plus的TenantLineHandler无缝集成
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 自定义实现类
 * @Service
 * public class CustomTenantIdServiceImpl implements GXTenantIdService {
 *     @Autowired
 *     private TenantContextHolder tenantContextHolder;
 *     
 *     @Override
 *     public Expression getTenantId() {
 *         // 从当前请求上下文中获取租户ID
 *         Long tenantId = tenantContextHolder.getTenantId();
 *         if (tenantId == null) {
 *             // 如果未找到租户ID，可以返回默认值或抛出异常
 *             return new LongValue(1);
 *         }
 *         return new LongValue(tenantId);
 *     }
 *     
 *     @Override
 *     public boolean ignoreTable(String tableName, String tenantIdColumn) {
 *         // 可以自定义哪些表不需要进行租户过滤
 *         if ("sys_config".equals(tableName) || "common_dict".equals(tableName)) {
 *             return true;
 *         }
 *         // 调用默认实现进行判断
 *         return GXTenantIdService.super.ignoreTable(tableName, tenantIdColumn);
 *     }
 * }
 *
 * // 2. 在应用配置中启用多租户功能
 * // application.yml
 * // maple:
 * //   framework:
 * //     enable:
 * //       tenant: true
 *
 * // 3. 确保相关表中添加tenant_id字段
 * // CREATE TABLE `user` (
 * //   `id` bigint NOT NULL AUTO_INCREMENT,
 * //   `username` varchar(50) NOT NULL COMMENT '用户名',
 * //   `tenant_id` bigint NOT NULL COMMENT '租户ID',
 * //   ...
 * //   PRIMARY KEY (`id`)
 * // ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 * </pre>
 * </p>
 *
 * <p>
 * 安全注意事项：
 * - 确保租户ID的获取逻辑安全可靠，防止租户ID被篡改
 * - 对于敏感操作，建议添加额外的租户验证
 * - 在实现ignoreTable方法时，谨慎决定哪些表可以跨租户访问
 * - 避免在日志中输出租户ID等敏感信息
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
public interface GXTenantIdService {
    /**
     * 获取当前租户ID
     * <p>
     * 返回一个SQL表达式，用于在SQL查询中添加租户过滤条件。
     * 默认实现返回固定值0，实际应用中应该根据系统上下文动态获取租户ID。
     * </p>
     *
     * <p>
     * 实现此方法时应注意：
     * - 返回值不应为null，至少提供一个默认值
     * - 考虑租户ID获取失败的情况，提供合理的错误处理
     * - 可以从请求头、线程上下文、安全上下文等获取租户ID
     * </p>
     *
     * @return 表示租户ID的SQL表达式对象
     */
    default Expression getTenantId() {
        return new LongValue(0);
    }

    /**
     * 根据表名判断是否忽略拼接多租户条件
     * <p>
     * 默认都要进行解析并拼接多租户条件
     * 该方法使用缓存优化表结构检查，避免重复查询表信息
     * 缓存结果存储在FRAMEWORK-CACHE中，键为表名，值为是否忽略租户条件
     * </p>
     *
     * <p>
     * 实现此方法时应注意：
     * - 全局配置表、字典表等通常应该忽略租户条件
     * - 可以通过表名前缀或后缀来批量判断
     * - 缓存的使用可以显著提高性能，尤其在大型系统中
     * </p>
     *
     * @param tableName      表名
     * @param tenantIdColumn 租户字段名
     * @return 是否忽略, true:表示忽略，false:需要解析并拼接多租户条件
     */
    default boolean ignoreTable(String tableName, String tenantIdColumn) {
        // 数据缓存管理器
        // 用于缓存表结构信息，减少重复查询，提高性能
        // 使用Spring的CaffeineCacheManager，支持自动过期和大小限制
        CaffeineCacheManager caffeineCacheManager = GXSpringContextUtils.getBean(CaffeineCacheManager.class);
        if (caffeineCacheManager == null) {
            // 安全处理：如果缓存管理器不可用，默认不忽略租户条件
            return false;
        }
        
        Cache cache = caffeineCacheManager.getCache("FRAMEWORK-CACHE");
        if (cache == null) {
            // 安全处理：如果缓存不可用，默认不忽略租户条件
            return false;
        }

        // 先从缓存中获取结果，避免重复查询
        Boolean hasTenantIdField = cache.get(tableName, Boolean.class);
        if (Objects.nonNull(hasTenantIdField)) {
            return hasTenantIdField;
        }

        // 缓存未命中，查询表结构
        TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
        if (Objects.isNull(tableInfo)) {
            // 表不存在，缓存结果并返回true
            cache.put(tableName, Boolean.TRUE);
            return true;
        }

        // 检查表是否包含租户ID字段
        boolean contains = !CollUtil.contains(tableInfo.getFieldList(),
                field -> CharSequenceUtil.equalsIgnoreCase(field.getColumn(), tenantIdColumn));

        // 缓存结果
        cache.put(tableName, contains);
        return contains;
    }
}
