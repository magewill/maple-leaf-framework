package cn.maple.canal.service;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;

/**
 * Table-level Canal event handler contract.
 */
public interface GXProcessCanalDataService {
    Dict processUpdate(GXCanalDataDto canalData, Dict param);

    Dict processInsert(GXCanalDataDto canalData, Dict param);

    Dict processDelete(GXCanalDataDto canalData, Dict param);
}
