package cn.maple.core.datasource.dto;

import cn.maple.core.datasource.annotation.GXDataFilter;

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

    public String getSqlFilter() {
        return sqlFilter;
    }

    public void setSqlFilter(String sqlFilter) {
        this.sqlFilter = sqlFilter;
    }

    public GXDataFilter getDataFilter() {
        return dataFilter;
    }

    public void setDataFilter(GXDataFilter dataFilter) {
        this.dataFilter = dataFilter;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }
}
