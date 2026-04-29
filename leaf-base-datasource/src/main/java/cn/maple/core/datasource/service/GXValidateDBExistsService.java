package cn.maple.core.datasource.service;

import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 验证数据库中是否存在记录的服务接口
 * <p>
 * 该接口定义了验证数据库中是否存在特定记录的方法，支持多种验证场景，如唯一性检查、关联性验证等。
 * 增强版本支持异步验证、批量验证和更多的性能优化。
 * </p>
 *
 * <p>
 * 线程安全说明：
 * 实现类应确保线程安全，因为验证方法可能在并发环境中被调用。建议：
 * - 不维护共享可变状态
 * - 使用线程安全的工具类和数据结构
 * - 对外部资源的访问使用适当的同步机制
 * - 缓存实现应使用线程安全的容器，如ConcurrentHashMap
 * </p>
 *
 * <p>
 * 性能优化建议：
 * - 使用缓存减少重复验证的数据库查询
 * - 对于高频验证场景，考虑使用本地缓存
 * - 批量验证时，优化数据库查询，减少连接次数
 * - 异步验证可以减少阻塞等待时间
 * - 使用索引字段进行验证，提高查询效率
 * </p>
 *
 * <p>
 * 使用示例1：基本验证
 * <pre>
 * GXValidateExistsDto dto = GXValidateExistsDto.builder()
 *     .tableName("tb_user")
 *     .fieldName("username")
 *     .value("john")
 *     .build();
 * boolean exists = validateExists(dto, context);
 * </pre>
 * </p>
 *
 * <p>
 * 使用示例2：带条件的验证
 * <pre>
 * Dict condition = Dict.create().set("status", 1);
 * GXValidateExistsDto dto = GXValidateExistsDto.builder()
 *     .tableName("tb_product")
 *     .fieldName("product_id")
 *     .value(123)
 *     .condition(condition)
 *     .build();
 * boolean exists = validateExists(dto, context);
 * </pre>
 * </p>
 *
 * <p>
 * 使用示例3：异步验证
 * <pre>
 * GXValidateExistsDto dto = GXValidateExistsDto.builder()
 *     .tableName("tb_order")
 *     .fieldName("order_no")
 *     .value("ORD20230101")
 *     .build();
 * CompletableFuture<Boolean> future = validateExistsAsync(dto);
 * future.thenAccept(exists -> {
 *     // 处理验证结果
 * });
 * </pre>
 * </p>
 *
 * <p>
 * 使用示例4：批量验证
 * <pre>
 * List<GXValidateExistsDto> dtoList = new ArrayList<>();
 * // 添加多个验证DTO
 * List<Boolean> results = validateExistsBatch(dtoList, context);
 * </pre>
 * </p>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
public interface GXValidateDBExistsService {
    /**
     * 验证数据是否存在
     * <p>
     * 该方法检查数据库中是否存在符合条件的记录。
     * 验证逻辑基于提供的GXValidateExistsDto参数，支持多种验证场景。
     * </p>
     * <p>
     * 线程安全：该方法应确保线程安全，不依赖共享可变状态。
     * 性能：实现类可以考虑使用缓存优化高频验证场景。
     * </p>
     *
     * @param validateExistsDto 验证参数DTO，包含表名、字段名、值和条件等信息
     * @param context           验证上下文，包含验证过程中的环境信息
     * @return boolean 存在返回true，不存在返回false
     */
    boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext context);

    /**
     * 异步验证数据是否存在
     * <p>
     * 该方法提供异步验证能力，适用于不需要立即获取结果的场景。
     * 返回CompletableFuture对象，可以通过回调处理验证结果。
     * </p>
     * <p>
     * 线程安全：该方法使用CompletableFuture确保线程安全。
     * 性能：异步执行可以减少阻塞等待时间，提高系统响应性。
     * </p>
     *
     * @param validateExistsDto 验证参数DTO，包含表名、字段名、值和条件等信息
     * @return CompletableFuture<Boolean> 异步验证结果，存在返回true，不存在返回false
     */
    default CompletableFuture<Boolean> validateExistsAsync(GXValidateExistsDto validateExistsDto) {
        return validateExistsAsync(validateExistsDto, resolveAsyncExecutor());
    }

    default CompletableFuture<Boolean> validateExistsAsync(GXValidateExistsDto validateExistsDto, Executor executor) {
        if (validateExistsDto == null) {
            return CompletableFuture.completedFuture(false);
        }
        Objects.requireNonNull(executor, "Executor must not be null");
        Callable<Boolean> validateTask = () -> validateExists(validateExistsDto, null);
        Callable<Boolean> filterAwareTask = GXDataFilterThreadLocalUtils.wrap(validateTask);
        Callable<Boolean> task = GXDynamicContextHolder.wrap(filterAwareTask);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private static Executor resolveAsyncExecutor() {
        Executor executor = GXSpringContextUtils.getBean("myBatisEventAsyncTaskExecutor", Executor.class);
        return executor != null ? executor : ForkJoinPool.commonPool();
    }

    /**
     * 批量验证数据是否存在
     * <p>
     * 该方法支持一次验证多个条件，适用于需要批量验证的场景。
     * 返回与输入列表大小相同的结果列表，每个结果对应一个验证条件。
     * </p>
     * <p>
     * 线程安全：该方法应确保线程安全，不依赖共享可变状态。
     * 性能：实现类应优化数据库查询，减少连接次数，提高批量验证效率。
     * </p>
     *
     * @param validateExistsDtoList 验证参数DTO列表
     * @param context               验证上下文
     * @return List<Boolean> 验证结果列表，与输入列表顺序一致
     */
    default List<Boolean> validateExistsBatch(List<GXValidateExistsDto> validateExistsDtoList, ConstraintValidatorContext context) {
        if (validateExistsDtoList == null || validateExistsDtoList.isEmpty()) {
            return Collections.emptyList();
        }
        return validateExistsDtoList.stream()
                .map(dto -> Objects.nonNull(dto) && validateExists(dto, context))
                .toList();
    }

    /**
     * 简化验证方法，直接使用表名、字段名和值进行验证
     * <p>
     * 该方法是validateExists的简化版本，适用于简单验证场景。
     * 自动构建GXValidateExistsDto对象，减少代码冗余。
     * </p>
     * <p>
     * 线程安全：该方法应确保线程安全，不依赖共享可变状态。
     * 性能：实现类可以考虑使用缓存优化高频验证场景。
     * </p>
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @param value     值
     * @param context   验证上下文
     * @return boolean 存在返回true，不存在返回false
     */
    default boolean quickValidateExists(String tableName, String fieldName, Object value, ConstraintValidatorContext context) {
        if (tableName == null || tableName.isBlank() || fieldName == null || fieldName.isBlank()) {
            return false;
        }
        GXValidateExistsDto dto = GXValidateExistsDto.builder()
                .tableName(tableName)
                .fieldName(fieldName)
                .value(value)
                .build();
        return validateExists(dto, context);
    }
}
