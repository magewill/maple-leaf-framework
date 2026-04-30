package cn.maple.core.framework.dto.protocol.res;

import cn.maple.core.framework.dto.res.GXPaginationResDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class GXPaginationResProtocol<T> extends GXBaseResProtocol {
    @Serial
    private static final long serialVersionUID = -6977700102950116740L;

    private long total;

    private long pageSize;

    private long pages;

    private long currentPage;

    private transient List<T> records;

    public GXPaginationResProtocol(List<T> list, long totalCount, long pages, long pageSize, long currPage) {
        this.records = list;
        this.total = totalCount;
        this.pageSize = pageSize;
        this.currentPage = currPage;
        this.pages = pages;
    }

    public GXPaginationResProtocol(GXPaginationResDto<T> page) {
        this.records = page.getRecords();
        this.total = page.getTotal();
        this.pageSize = page.getPageSize();
        this.currentPage = page.getCurrentPage();
        this.pages = page.getPages();
    }

    public GXPaginationResProtocol(List<T> list) {
        this.records = list;
    }
}
