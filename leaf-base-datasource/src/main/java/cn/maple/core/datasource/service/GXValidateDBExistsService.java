package cn.maple.core.datasource.service;

import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import cn.maple.core.framework.dto.inner.GXValidateExistsDto;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public interface GXValidateDBExistsService {
    private static Executor resolveAsyncExecutor() {
        Executor executor = GXSpringContextUtils.getBean("myBatisEventAsyncTaskExecutor", Executor.class);
        return executor != null ? executor : ForkJoinPool.commonPool();
    }

    boolean validateExists(GXValidateExistsDto validateExistsDto, ConstraintValidatorContext context);

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

    default List<Boolean> validateExistsBatch(List<GXValidateExistsDto> validateExistsDtoList, ConstraintValidatorContext context) {
        if (validateExistsDtoList == null || validateExistsDtoList.isEmpty()) {
            return Collections.emptyList();
        }
        return validateExistsDtoList.stream()
                .map(dto -> Objects.nonNull(dto) && validateExists(dto, context))
                .toList();
    }

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
