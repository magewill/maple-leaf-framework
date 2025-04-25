package cn.maple.canal.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.service.GXProcessCanalDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Canal数据处理服务默认实现类
 * <p>
 * 该类提供了Canal数据处理的默认实现，当没有为特定表配置专用处理服务时，将使用此默认实现。
 * 默认实现记录基本日志信息，并可作为自定义实现的参考模板。
 * </p>
 * <p>
 * 使用场景：
 * 1. 作为系统启动时的兜底服务
 * 2. 对不需要特殊处理的表提供基础日志记录
 * 3. 作为自定义实现的模板参考
 * </p>
 */
@Slf4j
@Service("defaultProcessCanalDataService")
public class GXDefaultProcessCanalDataServiceImpl implements GXProcessCanalDataService {
    /**
     * 处理数据库更新操作的默认实现
     * <p>
     * 记录更新操作的基本信息，包括数据库名、表名、影响行数等。
     * 此默认实现不执行实际业务逻辑，仅作为日志记录和兜底处理。
     * </p>
     *
     * @param canalData canal解析出来的数据，包含新旧数据、表信息等完整变更内容
     * @param param     额外参数，可传入操作人、来源系统等上下文信息
     * @return Dict 处理结果，包含处理状态和基本信息
     */
    @Override
    public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
        log.debug("默认UPDATE处理：数据库[{}]表[{}]，共[{}]条记录", 
                canalData.getDatabase(), 
                canalData.getTable(), 
                canalData.getData() != null ? canalData.getData().size() : 0);
        return Dict.create()
                .set("status", "success")
                .set("action", "update")
                .set("table", canalData.getTable())
                .set("database", canalData.getDatabase());
    }

    /**
     * 处理数据库插入操作的默认实现
     * <p>
     * 记录插入操作的基本信息，包括数据库名、表名、新增行数等。
     * 此默认实现不执行实际业务逻辑，仅作为日志记录和兜底处理。
     * </p>
     *
     * @param canalData canal解析出来的数据，包含新插入的数据、表信息等完整内容
     * @param param     额外参数，可传入操作人、来源系统等上下文信息
     * @return Dict 处理结果，包含处理状态和基本信息
     */
    @Override
    public Dict processInsert(GXCanalDataDto canalData, Dict param) {
        log.debug("默认INSERT处理：数据库[{}]表[{}]，共[{}]条记录", 
                canalData.getDatabase(), 
                canalData.getTable(), 
                canalData.getData() != null ? canalData.getData().size() : 0);
        return Dict.create()
                .set("status", "success")
                .set("action", "insert")
                .set("table", canalData.getTable())
                .set("database", canalData.getDatabase());
    }

    /**
     * 处理数据库删除操作的默认实现
     * <p>
     * 记录删除操作的基本信息，包括数据库名、表名、删除行数等。
     * 此默认实现不执行实际业务逻辑，仅作为日志记录和兜底处理。
     * </p>
     *
     * @param canalData canal解析出来的数据，包含被删除的数据、表信息等完整内容
     * @param param     额外参数，可传入操作人、来源系统等上下文信息
     * @return Dict 处理结果，包含处理状态和基本信息
     */
    @Override
    public Dict processDelete(GXCanalDataDto canalData, Dict param) {
        log.debug("默认DELETE处理：数据库[{}]表[{}]，共[{}]条记录", 
                canalData.getDatabase(), 
                canalData.getTable(), 
                canalData.getData() != null ? canalData.getData().size() : 0);
        return Dict.create()
                .set("status", "success")
                .set("action", "delete")
                .set("table", canalData.getTable())
                .set("database", canalData.getDatabase());
    }
}
