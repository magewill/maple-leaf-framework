package cn.maple.core.framework.api.dto.req;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
public class GXRpcQueryParamApiReqDto extends GXBaseApiReqDto implements Serializable {
    private String tableName;

    private String tableNameAlias;

    private Integer page = GXCommonConstant.DEFAULT_CURRENT_PAGE;

    private Integer pageSize = GXCommonConstant.DEFAULT_PAGE_SIZE;

    private List<GXCondition<?>> condition;

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

    private boolean ignoreDataFilter = Boolean.FALSE;
}
