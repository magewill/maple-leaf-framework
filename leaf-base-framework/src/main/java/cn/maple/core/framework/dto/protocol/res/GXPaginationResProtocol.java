package cn.maple.core.framework.dto.protocol.res;

import cn.maple.core.framework.dto.res.GXPaginationResDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.ArrayList;
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

    private List<T> records;

    public GXPaginationResProtocol(List<T> list, long totalCount, long pages, long pageSize, long currPage) {
        setRecords(list);
        this.total = totalCount;
        this.pageSize = pageSize;
        this.currentPage = currPage;
        this.pages = pages;
    }

    public GXPaginationResProtocol(GXPaginationResDto<T> page) {
        setRecords(page.getRecords());
        this.total = page.getTotal();
        this.pageSize = page.getPageSize();
        this.currentPage = page.getCurrentPage();
        this.pages = page.getPages();
    }

    public GXPaginationResProtocol(List<T> list) {
        setRecords(list);
    }

    public List<T> getRecords() {
        if (records == null) {
            records = new ArrayList<>();
        }
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records == null ? new ArrayList<>() : new ArrayList<>(records);
    }
}
