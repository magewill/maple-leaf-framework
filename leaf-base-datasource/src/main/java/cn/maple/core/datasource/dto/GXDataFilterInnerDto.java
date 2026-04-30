package cn.maple.core.datasource.dto;

public class GXDataFilterInnerDto {
    private String sqlFilter;

    public GXDataFilterInnerDto(String sqlFilter) {
        this.sqlFilter = sqlFilter;
    }

    public String getSqlFilter() {
        return sqlFilter;
    }

    public void setSqlFilter(String sqlFilter) {
        this.sqlFilter = sqlFilter;
    }

    @Override
    public String toString() {
        return this.sqlFilter;
    }
}