package cn.maple.canal.service;

import cn.hutool.core.lang.Dict;

/**
 * Parse raw Canal message and return processing result.
 */
public interface GXCanalMessageParseService {
    Dict parseMessage(String message);
}
