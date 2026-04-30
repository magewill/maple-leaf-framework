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

@Slf4j
public class GXValidateDBExistsValidator implements ConstraintValidator<GXValidateDBExists, Object> {
    private static final ConcurrentMap<String, CacheEntry> RESULT_CACHE = new ConcurrentHashMap<>();

    private GXValidateDBExistsService service;

    private String fieldName;

    private Class<?>[] groups;

    private String tableName;

    private String condition;

    private String spEL;

    private String[] dependOnFields;

    private boolean enableCache;

    private int cacheExpireSeconds;

    public static void cleanExpiredCache() {
        RESULT_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

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

    @Override
    public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(o)) {
            return true;
        }

        if (null == service) {
            log.error("validateExists service is null, fieldName: {}, value: {}", fieldName, o);
            return false;
        }

        GXValidateExistsDto validateExistsDto = buildValidateExistsDto(o);

        if (enableCache) {
            cleanExpiredCache();
            String cacheKey = generateCacheKey(validateExistsDto);
            CacheEntry cacheEntry = RESULT_CACHE.get(cacheKey);

            if (cacheEntry != null && !cacheEntry.isExpired()) {
                log.debug("从缓存获取验证结果: {}, 字段: {}, 值: {}", cacheEntry.result(), fieldName, o);
                return cacheEntry.result();
            }

            boolean result = service.validateExists(validateExistsDto, constraintValidatorContext);
            long expireTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cacheExpireSeconds);
            RESULT_CACHE.put(cacheKey, new CacheEntry(result, expireTime));
            return result;
        }

        return service.validateExists(validateExistsDto, constraintValidatorContext);
    }

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

    private record CacheEntry(boolean result, long expireTime) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
