package cn.maple.core.framework.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuppressWarnings("all")
public class GXPaginationResDto<T> extends GXBaseResDto {
    private long total;

    private long pageSize;

    private long pages;

    private long currentPage;

    private List<T> records;

    public GXPaginationResDto(List<T> list, long totalCount, long pageSize, long currPage) {
        this.records = list;
        this.total = totalCount;
        this.pageSize = pageSize;
        this.currentPage = currPage;
        this.pages = (int) Math.ceil((double) totalCount / pageSize);
    }

    public GXPaginationResDto(List<T> list, long totalCount, long pages, long pageSize, long currPage) {
        this.records = list;
        this.total = totalCount;
        this.pageSize = pageSize;
        this.currentPage = currPage;
        this.pages = pages;
    }

    public GXPaginationResDto(List<T> list) {
        this.records = list;
    }
}
