package cn.maple.core.datasource.util;

import cn.hutool.core.thread.ThreadUtil;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;

/**
 * 数据过滤线程本地工具类
 * <p>
 * 该工具类用于在线程内部存储和管理数据过滤条件，基于ThreadLocal实现线程隔离。
 * 主要用于在多线程环境下安全地传递SQL过滤条件，避免线程间数据污染。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 设置数据过滤条件
 * GXDataFilterInnerDto filterDto = new GXDataFilterInnerDto("shop_id = 1");
 * GXDataFilterThreadLocalUtils.setDataFilterInnerDto(filterDto);
 *
 * // 获取数据过滤条件
 * GXDataFilterInnerDto dto = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
 *
 * // 操作完成后，清理ThreadLocal资源，避免内存泄漏
 * GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
 * </pre>
 * </p>
 * <p>
 * 注意：使用ThreadLocal时必须注意在使用完毕后调用cleanDataFilterInnerDto()方法清理资源，
 * 特别是在使用线程池的场景下，否则可能导致内存泄漏或数据错误。
 * </p>
 */
public class GXDataFilterThreadLocalUtils {
    /**
     * 存储数据过滤条件的ThreadLocal对象
     * 使用inheritable=true参数，支持子线程继承父线程的数据
     */
    private static final ThreadLocal<GXDataFilterInnerDto> DATA_FILTER_INNER_DTO = ThreadUtil.createThreadLocal(true);

    /**
     * 私有构造函数，防止实例化
     */
    private GXDataFilterThreadLocalUtils() {
        // 工具类不应被实例化
    }

    /**
     * 获取当前线程的数据过滤条件
     *
     * @return 数据过滤条件对象，如果未设置则可能返回null
     */
    public static GXDataFilterInnerDto getDataFilterInnerDto() {
        return DATA_FILTER_INNER_DTO.get();
    }

    /**
     * 设置当前线程的数据过滤条件
     *
     * @param dto 数据过滤条件对象
     */
    public static void setDataFilterInnerDto(GXDataFilterInnerDto dto) {
        DATA_FILTER_INNER_DTO.set(dto);
    }

    /**
     * 清理当前线程的数据过滤条件
     * 在使用完ThreadLocal后，应当调用此方法清理资源，避免内存泄漏
     */
    public static void cleanDataFilterInnerDto() {
        DATA_FILTER_INNER_DTO.remove();
    }
}
