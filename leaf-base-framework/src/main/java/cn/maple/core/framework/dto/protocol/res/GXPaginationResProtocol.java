package cn.maple.core.framework.dto.protocol.res;

import cn.maple.core.framework.dto.res.GXPaginationResDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 分页响应协议类
 * <p>
 * 该类用于封装分页查询的响应数据，包括分页信息（总记录数、每页记录数、总页数、当前页数）和实际数据列表
 * 主要用于前后端交互中的分页数据返回，支持多种构造方式，便于适应不同的分页场景
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 方式一：直接使用分页数据构造
 * List<UserDto> userList = userService.getUserList(page, pageSize);
 * long total = userService.countUsers();
 * long pages = (total + pageSize - 1) / pageSize;
 * GXPaginationResProtocol<UserDto> response = new GXPaginationResProtocol<>(userList, total, pages, pageSize, page);
 *
 * // 方式二：从GXPaginationResDto转换
 * GXPaginationResDto<UserDto> pageDto = userService.getUserPage(page, pageSize);
 * GXPaginationResProtocol<UserDto> response = new GXPaginationResProtocol<>(pageDto);
 *
 * // 方式三：仅包含列表数据（不分页）
 * List<UserDto> allUsers = userService.getAllUsers();
 * GXPaginationResProtocol<UserDto> response = new GXPaginationResProtocol<>(allUsers);
 *
 * // 在Controller中返回
 * @GetMapping("/users")
 * public GXPaginationResProtocol<UserDto> listUsers(@RequestParam(defaultValue = "1") int page,
 *                                                 @RequestParam(defaultValue = "10") int pageSize) {
 *     GXPaginationResDto<UserDto> pageDto = userService.getUserPage(page, pageSize);
 *     return new GXPaginationResProtocol<>(pageDto);
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明：
 * 1. 分页数据中不应包含敏感信息，必要时应进行脱敏处理
 * 2. 对于大数据量查询，应当限制最大页大小，防止资源耗尽攻击
 * 3. 分页参数应当进行合理性验证，避免非法参数导致的异常
 * </p>
 *
 * <p>
 * 性能优化说明：
 * 1. 使用transient关键字标记records字段，避免在序列化时包含过大的数据量
 * 2. 对于大数据量分页，应当使用高效的分页查询方式，如物理分页而非内存分页
 * 3. 考虑使用缓存机制缓存热门页的数据，减少数据库查询压力
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GXPaginationResProtocol<T> extends GXBaseResProtocol {
    @Serial
    private static final long serialVersionUID = -6977700102950116740L;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 每页记录数
     */
    private long pageSize;

    /**
     * 总页数
     */
    private long pages;

    /**
     * 当前页数
     */
    private long currentPage;

    /**
     * 列表数据
     */
    private transient List<T> records;

    /**
     * 分页
     *
     * @param list       列表数据
     * @param totalCount 总记录数
     * @param pages      总页数
     * @param pageSize   每页记录数
     * @param currPage   当前页数
     */
    public GXPaginationResProtocol(List<T> list, long totalCount, long pages, long pageSize, long currPage) {
        this.records = list;
        this.total = totalCount;
        this.pageSize = pageSize;
        this.currentPage = currPage;
        this.pages = pages;
    }

    /**
     * 分页
     */
    public GXPaginationResProtocol(GXPaginationResDto<T> page) {
        this.records = page.getRecords();
        this.total = page.getTotal();
        this.pageSize = page.getPageSize();
        this.currentPage = page.getCurrentPage();
        this.pages = page.getPages();
    }

    /**
     * 分页
     */
    public GXPaginationResProtocol(List<T> list) {
        this.records = list;
    }
}
