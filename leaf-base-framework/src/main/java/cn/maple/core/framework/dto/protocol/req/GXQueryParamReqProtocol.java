package cn.maple.core.framework.dto.protocol.req;

import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
public class GXQueryParamReqProtocol extends GXBaseReqProtocol {
    private static final long serialVersionUID = -7685836286570517029L;

    private Integer page = GXCommonConstant.DEFAULT_CURRENT_PAGE;

    private Integer pageSize = GXCommonConstant.DEFAULT_PAGE_SIZE;

    private List<GXCondition<?>> condition;

    private Map<String, String> orderByField;

    private Set<String> columns;

    private Set<String> groupByField;

    private Object extraData;
}
