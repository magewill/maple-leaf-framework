package cn.maple.core.framework.properties;

import cn.maple.core.framework.dto.GXBaseData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GXCaffeineCacheProperties extends GXBaseData {
    private Integer initialCapacity;

    private Long maximumSize;

    private Long maximumWeight;

    private Integer expireAfterAccess;

    private Integer expireAfterWrite;

    private Integer refreshAfterWrite;

    private Boolean weakKeys = Boolean.FALSE;

    private Boolean weakValues = Boolean.FALSE;

    private Boolean softValues = Boolean.FALSE;

    private Boolean recordStats = Boolean.FALSE;
}
