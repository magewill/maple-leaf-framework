package cn.maple.canal.service;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;

/**
 * Canal数据处理服务接口
 * <p>
 * 该接口定义了处理Canal捕获的数据库变更事件的方法，包括插入、更新和删除操作。
 * 实现此接口可以自定义不同数据库表的数据同步处理逻辑。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 为特定数据库表创建处理服务实现
 * @Service("db_table_Service")
 * public class DbTableProcessCanalDataServiceImpl implements GXProcessCanalDataService {
 *     @Override
 *     public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
 *         // 处理更新操作的逻辑
 *         List<Dict> data = canalData.getData(); // 获取新数据
 *         List<Dict> old = canalData.getOld();  // 获取旧数据
 *         // 执行业务逻辑...
 *         return Dict.create().set("status", "success");
 *     }
 *     
 *     // 实现其他方法...
 * }
 * </pre>
 * </p>
 */
public interface GXProcessCanalDataService {
    /**
     * 处理数据库更新操作
     * <p>
     * 当数据库中的记录被更新时，Canal会捕获此变更并通过该方法处理。
     * 方法接收完整的变更数据，包括新值和旧值，可用于实现数据同步、缓存更新、业务通知等功能。
     * </p>
     *
     * @param canalData canal解析出来的数据，包含新旧数据、表信息等完整变更内容
     * @param param     额外参数，可传入操作人、来源系统等上下文信息
     * @return Dict 处理结果，可包含处理状态、影响行数等信息
     */
    Dict processUpdate(GXCanalDataDto canalData, Dict param);

    /**
     * 处理数据库插入操作
     * <p>
     * 当数据库中插入新记录时，Canal会捕获此变更并通过该方法处理。
     * 方法接收完整的新增数据，可用于实现数据同步、缓存更新、业务通知等功能。
     * </p>
     *
     * @param canalData canal解析出来的数据，包含新插入的数据、表信息等完整内容
     * @param param     额外参数，可传入操作人、来源系统等上下文信息
     * @return Dict 处理结果，可包含处理状态、新记录ID等信息
     */
    Dict processInsert(GXCanalDataDto canalData, Dict param);

    /**
     * 处理数据库删除操作
     * <p>
     * 当数据库中的记录被删除时，Canal会捕获此变更并通过该方法处理。
     * 方法接收被删除的数据信息，可用于实现数据同步、缓存清理、业务通知等功能。
     * </p>
     *
     * @param canalData canal解析出来的数据，包含被删除的数据、表信息等完整内容
     * @param param     额外参数，可传入操作人、来源系统等上下文信息
     * @return Dict 处理结果，可包含处理状态、影响行数等信息
     */
    Dict processDelete(GXCanalDataDto canalData, Dict param);
}
