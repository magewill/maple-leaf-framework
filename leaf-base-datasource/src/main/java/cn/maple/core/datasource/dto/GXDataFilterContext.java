package cn.maple.core.datasource.dto;

import cn.maple.core.datasource.annotation.GXDataFilter;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GXDataFilterContext {
    private String sqlFilter;

    private GXDataFilter dataFilter;

    private String methodName;

    private boolean ignored;

    public GXDataFilterContext(String sqlFilter) {
        this(sqlFilter, null, null, false);
    }

    public GXDataFilterContext(String sqlFilter, GXDataFilter dataFilter, String methodName, boolean ignored) {
        this.sqlFilter = sqlFilter;
        this.dataFilter = dataFilter;
        this.methodName = methodName;
        this.ignored = ignored;
    }

    public GXDataFilterContext(GXDataFilterContext context) {
        this(context.getSqlFilter(), context.getDataFilter(), context.getMethodName(), context.isIgnored());
    }
}
