package cn.maple.core.framework.dto.inner;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.maple.core.framework.dto.GXBaseDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
@Data
@Builder
public class GXBaseQueryParamInnerDto extends GXBaseDto {
    private String tableName;

    private String tableNameAlias;

    private Integer page;

    private Integer pageSize;

    @Builder.Default
    private List<GXCondition<?>> condition = new ArrayList<>();

    private Set<String> columns;

    private Map<String, String> orderByField;

    private Set<String> groupByField;

    private String methodName;

    private Set<String> having;

    private CopyOptions copyOptions;

    private Integer limit;

    private List<GXJoinDto> joins;

    private Object extraData;

    private String rawSQL;

    @Builder.Default
    private boolean ignoreDataFilter = Boolean.FALSE;

    @Builder.Default
    private boolean paginateCount = Boolean.TRUE;

    @Builder.Default
    private Map<String, Object> paramMap = new ConcurrentHashMap<>();
}
