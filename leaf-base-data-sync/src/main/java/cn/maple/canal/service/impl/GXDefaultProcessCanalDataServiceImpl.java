package cn.maple.canal.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.service.GXProcessCanalDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("defaultProcessCanalDataService")
public class GXDefaultProcessCanalDataServiceImpl implements GXProcessCanalDataService {
    @Override
    public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
        log.debug("Default UPDATE: database[{}] table[{}] rows[{}]",
                canalData.getDatabase(),
                canalData.getTable(),
                canalData.getData() != null ? canalData.getData().size() : 0);
        return Dict.create()
                .set("status", "success")
                .set("action", "update")
                .set("table", canalData.getTable())
                .set("database", canalData.getDatabase());
    }

    @Override
    public Dict processInsert(GXCanalDataDto canalData, Dict param) {
        log.debug("Default INSERT: database[{}] table[{}] rows[{}]",
                canalData.getDatabase(),
                canalData.getTable(),
                canalData.getData() != null ? canalData.getData().size() : 0);
        return Dict.create()
                .set("status", "success")
                .set("action", "insert")
                .set("table", canalData.getTable())
                .set("database", canalData.getDatabase());
    }

    @Override
    public Dict processDelete(GXCanalDataDto canalData, Dict param) {
        log.debug("Default DELETE: database[{}] table[{}] rows[{}]",
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
