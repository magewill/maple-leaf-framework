package cn.maple.core.framework.dto.protocol.req;

import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    private List<GXCondition<?>> condition = new ArrayList<>();

    private Map<String, String> orderByField = new LinkedHashMap<>();

    private Set<String> columns = new LinkedHashSet<>();

    private Set<String> groupByField = new LinkedHashSet<>();

    private Object extraData;

    public void setPage(Integer page) {
        this.page = page == null || page <= 0 ? GXCommonConstant.DEFAULT_CURRENT_PAGE : page;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize == null || pageSize <= 0 ? GXCommonConstant.DEFAULT_PAGE_SIZE : pageSize;
    }

    public List<GXCondition<?>> getCondition() {
        return Collections.unmodifiableList(condition);
    }

    public void setCondition(List<GXCondition<?>> condition) {
        this.condition = condition == null ? new ArrayList<>() : new ArrayList<>(condition);
    }

    public Map<String, String> getOrderByField() {
        return Collections.unmodifiableMap(orderByField);
    }

    public void setOrderByField(Map<String, String> orderByField) {
        this.orderByField = orderByField == null ? new LinkedHashMap<>() : new LinkedHashMap<>(orderByField);
    }

    public Set<String> getColumns() {
        return Collections.unmodifiableSet(columns);
    }

    public void setColumns(Set<String> columns) {
        this.columns = columns == null ? new LinkedHashSet<>() : new LinkedHashSet<>(columns);
    }

    public Set<String> getGroupByField() {
        return Collections.unmodifiableSet(groupByField);
    }

    public void setGroupByField(Set<String> groupByField) {
        this.groupByField = groupByField == null ? new LinkedHashSet<>() : new LinkedHashSet<>(groupByField);
    }
}
