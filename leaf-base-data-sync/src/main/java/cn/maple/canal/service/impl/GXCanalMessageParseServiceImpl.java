package cn.maple.canal.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.service.GXCanalMessageParseService;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Parse Canal JSON message and dispatch it to table-level handlers.
 */
@Slf4j
@Service
public class GXCanalMessageParseServiceImpl implements GXCanalMessageParseService {
    @Override
    public Dict parseMessage(String message) {
        String operator = "operator";
        Dict dict = Dict.create();

        if (CharSequenceUtil.isBlank(message)) {
            log.error("Received blank message");
            return dict.set("status", "error").set("message", "message is blank");
        }

        if (!JSONUtil.isTypeJSON(message)) {
            log.error("Invalid JSON message: {}", CharSequenceUtil.maxLength(message, 100));
            return dict.set("status", "error").set("message", "message is not valid JSON");
        }

        GXCanalDataDto canalDataDto;
        try {
            canalDataDto = JSONUtil.toBean(message, GXCanalDataDto.class);
        } catch (JSONException e) {
            log.error("JSON parse error: {}", e.getMessage());
            return dict.set("status", "error").set("message", "json parse failed: " + e.getMessage());
        }

        if (CharSequenceUtil.isBlank(canalDataDto.getDatabase()) || CharSequenceUtil.isBlank(canalDataDto.getTable())) {
            log.error("Database or table is blank");
            return dict.set("status", "error").set("message", "database or table is blank");
        }

        final String serviceName = CharSequenceUtil.toCamelCase(
                CharSequenceUtil.format("{}_{}_Service", canalDataDto.getDatabase(), canalDataDto.getTable()));
        log.debug("Try to resolve handler bean: {}", serviceName);

        Object bean = GXSpringContextUtils.getBean(serviceName);
        if (Objects.isNull(bean)) {
            log.debug("Bean [{}] not found, fallback to defaultProcessCanalDataService", serviceName);
            bean = GXSpringContextUtils.getBean("defaultProcessCanalDataService");
            if (Objects.isNull(bean)) {
                log.warn("No handler bean [{}] and no default handler", serviceName);
                return dict.set("status", "error").set("message", "no suitable handler found");
            }
        }

        if (!(bean instanceof GXProcessCanalDataService processCanalDataService)) {
            log.error("Bean [{}] is not {}", serviceName, GXProcessCanalDataService.class.getSimpleName());
            return dict.set("status", "error").set("message", "handler bean type mismatch");
        }

        String type = Optional.ofNullable(canalDataDto.getType()).orElse("");

        switch (type) {
            case "UPDATE":
                log.debug("Dispatch UPDATE: database[{}] table[{}]", canalDataDto.getDatabase(), canalDataDto.getTable());
                dict = processCanalDataService.processUpdate(canalDataDto, Dict.create().set(operator, "update"));
                break;
            case "INSERT":
                log.debug("Dispatch INSERT: database[{}] table[{}]", canalDataDto.getDatabase(), canalDataDto.getTable());
                dict = processCanalDataService.processInsert(canalDataDto, Dict.create().set(operator, "insert"));
                break;
            case "DELETE":
                log.debug("Dispatch DELETE: database[{}] table[{}]", canalDataDto.getDatabase(), canalDataDto.getTable());
                dict = processCanalDataService.processDelete(canalDataDto, Dict.create().set(operator, "delete"));
                break;
            default:
                log.warn("Unknown operation type: {}", type);
                dict.set("status", "error").set("message", "unknown operation type: " + type);
                break;
        }

        if (dict == null) {
            log.warn("Handler returned null Dict");
            dict = Dict.create().set("status", "warning").set("message", "handler returned null");
        }

        return dict;
    }
}
