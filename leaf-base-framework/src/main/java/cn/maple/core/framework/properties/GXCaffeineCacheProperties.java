package cn.maple.core.framework.properties;

import cn.maple.core.framework.dto.GXBaseData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Data
public class GXCaffeineCacheProperties extends GXBaseData {
    private @Nullable Integer initialCapacity;

    private @Nullable Long maximumSize;

    private @Nullable Long maximumWeight;

    private @Nullable Integer expireAfterAccess;

    private @Nullable Integer expireAfterWrite;

    private @Nullable Integer refreshAfterWrite;

    private Boolean weakKeys = Boolean.FALSE;

    private Boolean weakValues = Boolean.FALSE;

    private Boolean softValues = Boolean.FALSE;

    private Boolean recordStats = Boolean.FALSE;
}
