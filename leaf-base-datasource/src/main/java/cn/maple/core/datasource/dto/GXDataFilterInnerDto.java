package cn.maple.core.datasource.dto;

import cn.maple.core.datasource.annotation.GXDataFilter;

public class GXDataFilterInnerDto extends GXDataFilterContext {
    public GXDataFilterInnerDto(String sqlFilter) {
        super(sqlFilter);
    }

    public GXDataFilterInnerDto(String sqlFilter, GXDataFilter dataFilter, String methodName, boolean ignored) {
        super(sqlFilter, dataFilter, methodName, ignored);
    }

    public GXDataFilterInnerDto(GXDataFilterContext context) {
        super(context);
    }

    @Override
    public String toString() {
        return getSqlFilter();
    }
}
