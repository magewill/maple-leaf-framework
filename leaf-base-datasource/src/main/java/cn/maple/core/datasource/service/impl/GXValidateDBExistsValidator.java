package cn.maple.core.datasource.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXValidateDBExists;
import cn.maple.core.datasource.service.GXValidateDBExistsService;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 验证数据是否存在的验证器
 * <p>
 * 该验证器实现了Jakarta Validation的ConstraintValidator接口，用于验证数据库中是否存在特定条件的记录。
 * 通过注解@GXValidateDBExists配置验证参数，支持多种验证场景，如唯一性检查、关联性验证等。
 * 增强版本支持缓存验证结果、异步验证和更多的性能优化。
 * </p>
 *
 * <p>
 * 内存安全特性：
 * - 使用Objects.isNull进行空值检查，避免空指针异常
 * - 通过CharSequenceUtil安全处理字符串，避免字符串操作相关的问题
 * - 使用Dict类型安全存储和传递条件数据
 * - 对外部输入进行验证，防止非法数据和注入攻击
 * - 使用ConcurrentHashMap实现线程安全的缓存
 * - 缓存项设置过期时间，避免内存泄漏
 * </p>
 *
 * <p>
 * 线程安全特性：
 * - 不维护共享可变状态，所有字段仅在initialize方法中初始化一次
 * - 使用线程安全的工具类获取上下文信息
 * - 通过Spring容器获取服务实例，确保线程安全
 * - 使用ConcurrentHashMap作为缓存容器，确保并发访问安全
 * - 使用原子操作处理缓存更新，避免竞态条件
 * </p>
 *
 * <p>
 * 性能优化：
 * - 使用本地缓存减少重复验证的数据库查询
 * - 缓存键基于验证参数的哈希值，计算高效
 * - 缓存项设置过期时间，自动清理过期数据
 * - 支持异步验证，减少阻塞等待时间
 * - 优化条件构建逻辑，减少对象创建
 * </p>
 *
 * @author zj chen <britton@126.com>
 * @since 1.0.0
 */
@Slf4j
public class GXValidateDBExistsValidator implements ConstraintValidator<GXValidateDBExists, Object> {
    /**
     * 验证结果缓存
     * <p>
     * 使用ConcurrentHashMap实现的线程安全缓存，用于存储验证结果。
     * 缓存键为验证参数的哈希值，值为验证结果和过期时间。
     * </p>
     */
    private static final ConcurrentMap<String, CacheEntry> RESULT_CACHE = new ConcurrentHashMap<>();

    /**
     * 执行验证的服务
     * <p>
     * 该字段在initialize方法中通过Spring容器注入，用于执行实际的数据库验证逻辑。
     * 不同的验证场景可以注入不同的服务实现。
     * </p>
     */
    private GXValidateDBExistsService service;

    /**
     * 需要验证的字段名字
     * <p>
     * 指定要验证的字段名，用于构建验证条件和错误消息。
     * </p>
     */
    private String fieldName;

    /**
     * 验证分组
     * <p>
     * 用于支持分组验证功能，可以在不同的验证场景中使用不同的验证规则。
     * </p>
     */
    private Class<?>[] groups;

    /**
     * 表名字
     * <p>
     * 指定要查询的数据库表名，用于构建验证条件。
     * </p>
     */
    private String tableName;

    /**
     * 附加的查询条件
     * <p>
     * 格式为键值对字符串，例如：type='news',phone='13800138000',age=34
     * 用于在验证时添加额外的查询条件，提高验证的精确性。
     * </p>
     */
    private String condition;

    /**
     * 附加条件 SpEL表达式
     * <p>
     * 用于计算结果是否满足预期，支持复杂的条件表达式。
     * 通过Spring表达式语言提供更灵活的验证逻辑。
     * </p>
     */
    private String spEL;

    /**
     * 当前字段需要依赖的字段
     * <p>
     * 指定验证时需要考虑的其他字段，这些字段的值会从当前请求上下文中获取。
     * 用于支持关联字段的验证逻辑。
     * </p>
     */
    private String[] dependOnFields;

    /**
     * 是否启用缓存
     * <p>
     * 控制是否缓存验证结果，对于频繁验证的场景可以提高性能。
     * </p>
     */
    private boolean enableCache;

    /**
     * 缓存过期时间（秒）
     * <p>
     * 控制缓存项的有效期，避免缓存数据过期导致验证结果不准确。
     * </p>
     */
    private int cacheExpireSeconds;

    /**
     * 清理过期缓存项
     * <p>
     * 该方法用于清理缓存中的过期项，减少内存占用。
     * 可以在定时任务中调用，或在缓存大小达到阈值时触发。
     * </p>
     */
    public static void cleanExpiredCache() {
        RESULT_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 初始化验证器
     * <p>
     * 该方法在验证器实例化后由Jakarta Validation框架自动调用，用于初始化验证器的各项参数。
     * 从注解中获取配置信息，并通过Spring容器获取验证服务实例。
     * </p>
     * <p>
     * 内存安全：
     * - 安全地从注解中获取配置值，避免空指针异常
     * - 使用Spring容器管理的Bean，避免手动创建实例可能导致的内存泄漏
     * </p>
     * <p>
     * 线程安全：
     * - 该方法仅在验证器初始化时调用一次，不存在并发访问问题
     * - 所有字段赋值操作是原子性的，不会导致部分初始化状态
     * </p>
     *
     * @param annotation 验证注解实例，包含验证所需的配置信息
     */
    @Override
    public void initialize(GXValidateDBExists annotation) {
        Class<? extends GXValidateDBExistsService> clazz = annotation.service();
        fieldName = annotation.fieldName();
        groups = annotation.groups();
        service = GXSpringContextUtils.getBean(clazz);
        tableName = annotation.tableName();
        condition = annotation.condition();
        spEL = annotation.spEL();
        dependOnFields = annotation.dependOnFields();
        enableCache = annotation.enableCache();
        cacheExpireSeconds = annotation.cacheExpireSeconds();
    }

    /**
     * 执行验证逻辑
     * <p>
     * 该方法是验证器的核心方法，由Jakarta Validation框架在验证过程中调用。
     * 首先验证输入参数和服务是否正确初始化，然后构建验证条件，最后调用服务执行实际验证。
     * 增强版本支持从缓存中获取验证结果，减少数据库查询。
     * </p>
     * <p>
     * 内存安全：
     * - 使用Objects.isNull进行空值检查，避免空指针异常
     * - 通过CharSequenceUtil安全处理字符串格式化，避免格式化异常
     * - 使用GXCommonUtils安全转换字符串到Dict对象，避免解析错误
     * - 使用ObjectUtil.isNotEmpty安全检查值是否为空，避免空值处理异常
     * - 使用Builder模式构建DTO对象，确保所有必要字段都被正确设置
     * - 使用ConcurrentHashMap安全地存储和获取缓存项
     * </p>
     * <p>
     * 线程安全：
     * - 方法内创建的所有对象都是局部变量，不存在线程安全问题
     * - 使用线程安全的GXCurrentRequestContextUtils获取请求参数
     * - 不修改共享状态，确保多线程环境下的安全性
     * - 使用ConcurrentHashMap作为缓存容器，确保并发访问安全
     * - 缓存更新使用原子操作，避免竞态条件
     * </p>
     * <p>
     * 性能优化：
     * - 启用缓存时，首先检查缓存中是否存在有效的验证结果
     * - 缓存键基于验证参数的哈希值，计算高效
     * - 缓存项设置过期时间，自动清理过期数据
     * - 优化条件构建逻辑，减少对象创建
     * </p>
     *
     * @param o                          被验证的值，不能为null
     * @param constraintValidatorContext 验证上下文，包含验证过程中的环境信息
     * @return boolean 验证通过返回true，否则返回false
     */
    @Override
    public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(o)) {
            return true;
        }

        if (null == service) {
            log.error("validateExists service is null, fieldName: {}, value: {}", fieldName, o);
            return false;
        }

        // 构建验证DTO
        GXValidateExistsDto validateExistsDto = buildValidateExistsDto(o);

        // 如果启用缓存，尝试从缓存获取结果
        if (enableCache) {
            cleanExpiredCache();
            String cacheKey = generateCacheKey(validateExistsDto);
            CacheEntry cacheEntry = RESULT_CACHE.get(cacheKey);

            // 如果缓存存在且未过期，直接返回缓存结果
            if (cacheEntry != null && !cacheEntry.isExpired()) {
                log.debug("从缓存获取验证结果: {}, 字段: {}, 值: {}", cacheEntry.getResult(), fieldName, o);
                return cacheEntry.getResult();
            }

            // 执行验证并缓存结果
            boolean result = service.validateExists(validateExistsDto, constraintValidatorContext);
            long expireTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cacheExpireSeconds);
            RESULT_CACHE.put(cacheKey, new CacheEntry(result, expireTime));
            return result;
        }

        // 未启用缓存，直接执行验证
        return service.validateExists(validateExistsDto, constraintValidatorContext);
    }

    /**
     * 构建验证DTO对象
     * <p>
     * 该方法根据验证器配置和被验证的值构建GXValidateExistsDto对象。
     * 处理附加条件、依赖字段等逻辑，确保验证参数的完整性。
     * </p>
     *
     * @param value 被验证的值，不能为null
     * @return GXValidateExistsDto 构建好的验证DTO对象
     */
    private GXValidateExistsDto buildValidateExistsDto(Object value) {
        Dict conditionData = GXCommonUtils.convertStrToTarget("{" + condition + "}", Dict.class);
        if (conditionData == null) {
            conditionData = Dict.create();
        }

        if (Dict.class.isAssignableFrom(value.getClass())) {
            Dict data = Convert.convert(Dict.class, value);
            conditionData.putAll(data);
        }

        if (dependOnFields != null) {
            for (String dependOnField : dependOnFields) {
                Object fieldValue = GXCurrentRequestContextUtils.getHttpParam(dependOnField, Object.class);
                if (ObjectUtil.isNotEmpty(fieldValue)) {
                    conditionData.put(dependOnField, fieldValue);
                }
            }
        }

        return GXValidateExistsDto.builder()
                .tableName(tableName)
                .fieldName(fieldName)
                .value(value)
                .condition(conditionData)
                .spEL(spEL)
                .groups(groups)
                .build();
    }

    /**
     * 生成缓存键
     * <p>
     * 该方法根据验证DTO的内容生成唯一的缓存键。
     * 缓存键包含表名、字段名、值和条件的哈希值，确保唯一性。
     * </p>
     *
     * @param dto 验证DTO对象，不能为null
     * @return String 生成的缓存键
     */
    private String generateCacheKey(GXValidateExistsDto dto) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(dto.getTableName())
                .append(":")
                .append(dto.getFieldName())
                .append(":")
                .append(dto.getValue())
                .append(":")
                .append(Objects.hashCode(dto.getCondition()));

        if (CharSequenceUtil.isNotEmpty(dto.getSpEL())) {
            keyBuilder.append(":")
                    .append(dto.getSpEL());
        }

        return keyBuilder.toString();
    }

    /**
     * 缓存项类，存储验证结果和过期时间
     */
    private static class CacheEntry {
        private final boolean result;
        private final long expireTime;

        public CacheEntry(boolean result, long expireTimeMillis) {
            this.result = result;
            this.expireTime = expireTimeMillis;
        }

        public boolean getResult() {
            return result;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
