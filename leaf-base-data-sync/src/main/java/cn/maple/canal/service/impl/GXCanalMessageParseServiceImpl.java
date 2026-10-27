package cn.maple.canal.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.enums.GXCanalOutcomeEnums;
import cn.maple.canal.service.GXCanalMessageParseService;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.canal.service.support.GXCanalHandlerResolver;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses Canal JSON payloads and dispatches them to table level handlers.
 *
 * <p>The payload is parsed exactly once; both a single object and a JSON array of objects are
 * accepted. Every event gets exactly one outcome.</p>
 *
 * <p>Handler failures follow the payload shape: a single object payload rethrows the handler exception
 * (the legacy contract), while a JSON array captures it per element as {@code RETRYABLE_FAILURE} so the
 * remaining elements are still processed.</p>
 */
@Slf4j
@Service
public class GXCanalMessageParseServiceImpl implements GXCanalMessageParseService {
    private static final String REASON_BLANK_MESSAGE = "message is blank";
    private static final String REASON_INVALID_JSON = "message is not valid JSON";
    private static final String REASON_INVALID_ELEMENT = "batch element is not a JSON object";
    private static final String REASON_NOT_OBJECT = "message is not a JSON object or array";
    private static final String REASON_JSON_CONVERT_FAILED = "json convert failed";
    private static final String REASON_MISSING_LOCATION = "database or table is blank";
    private static final String REASON_NO_HANDLER = "no suitable handler found";
    private static final String REASON_HANDLER_NULL = "handler returned null";

    private final GXCanalHandlerResolver handlerResolver;

    private final GXCanalLogThrottle logThrottle;

    public GXCanalMessageParseServiceImpl(GXCanalHandlerResolver handlerResolver, GXCanalLogThrottle logThrottle) {
        this.handlerResolver = handlerResolver;
        this.logThrottle = logThrottle;
    }

    @Override
    public List<GXCanalParseResultInnerDto> parse(String rawPayload) {
        if (CharSequenceUtil.isBlank(rawPayload)) {
            logThrottle.warn("blank-message", "Received blank Canal message");
            return List.of(GXCanalParseResultInnerDto.nonRetryableFailure(REASON_BLANK_MESSAGE, 0));
        }

        JSON parsed;
        try {
            parsed = JSONUtil.parse(rawPayload);
        } catch (JSONException e) {
            logThrottle.warn("invalid-json", "Invalid JSON Canal message: length={}, error={}",
                    rawPayload.length(), e.getMessage());
            return List.of(GXCanalParseResultInnerDto.nonRetryableFailure(REASON_INVALID_JSON, 0));
        }

        if (parsed instanceof JSONArray array) {
            return parseArray(array);
        }
        if (parsed instanceof JSONObject object) {
            return List.of(parseEvent(object, 0));
        }
        return List.of(GXCanalParseResultInnerDto.nonRetryableFailure(REASON_NOT_OBJECT, 0));
    }

    @Override
    public Dict parseMessage(String message) {
        List<GXCanalParseResultInnerDto> results = parse(message);
        GXCanalParseResultInnerDto first = results.get(0);
        GXCanalOutcomeEnums outcome = first.outcome();
        if (outcome == GXCanalOutcomeEnums.SUCCESS) {
            Dict handlerResult = first.handlerResult();
            return handlerResult == null ? Dict.create() : handlerResult;
        }
        if (outcome == GXCanalOutcomeEnums.SKIPPED) {
            return Dict.create()
                    .set(GXCanalConstant.RESULT_KEY_STATUS, GXCanalConstant.RESULT_STATUS_SUCCESS)
                    .set(GXCanalConstant.RESULT_KEY_MESSAGE, "skipped: " + first.reason());
        }
        String status = REASON_HANDLER_NULL.equals(first.reason())
                ? GXCanalConstant.RESULT_STATUS_WARNING
                : GXCanalConstant.RESULT_STATUS_ERROR;
        return Dict.create()
                .set(GXCanalConstant.RESULT_KEY_STATUS, status)
                .set(GXCanalConstant.RESULT_KEY_MESSAGE, first.reason());
    }

    private List<GXCanalParseResultInnerDto> parseArray(JSONArray array) {
        List<GXCanalParseResultInnerDto> results = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            Object element = array.get(index);
            if (element instanceof JSONObject object) {
                results.add(parseEventSafely(object, index));
            } else {
                logThrottle.warn("invalid-element", "Batch element is not a JSON object: index={}, type={}", index,
                        element == null ? "null" : element.getClass().getSimpleName());
                results.add(GXCanalParseResultInnerDto.nonRetryableFailure(REASON_INVALID_ELEMENT, index));
            }
        }
        return results;
    }

    /**
     * Batch elements are isolated from each other: a handler failure inside one element becomes a
     * retryable result for that element instead of aborting the remaining elements.
     */
    private GXCanalParseResultInnerDto parseEventSafely(JSONObject object, int index) {
        try {
            return parseEvent(object, index);
        } catch (RuntimeException e) {
            logThrottle.error("batch-element-failed",
                    "Canal batch element failed, remaining elements still processed: index={}, error={}",
                    index, e.getMessage());
            return GXCanalParseResultInnerDto.retryableFailure(describe(e), index);
        }
    }

    private String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private GXCanalParseResultInnerDto parseEvent(JSONObject object, int index) {
        GXCanalDataDto canalData;
        try {
            canalData = object.toBean(GXCanalDataDto.class);
        } catch (RuntimeException e) {
            logThrottle.warn("convert-failed", "Canal event conversion failed: index={}, error={}",
                    index, e.getMessage());
            return GXCanalParseResultInnerDto.nonRetryableFailure(REASON_JSON_CONVERT_FAILED, index);
        }
        if (canalData == null) {
            return GXCanalParseResultInnerDto.nonRetryableFailure(REASON_JSON_CONVERT_FAILED, index);
        }

        String type = Optional.ofNullable(canalData.getType()).orElse("").trim().toUpperCase(Locale.ROOT);
        if (Boolean.TRUE.equals(canalData.getIsDdl()) || GXCanalConstant.DDL_OPERATION_TYPES.contains(type)) {
            log.debug("Skip DDL event: database={}, table={}, type={}, index={}",
                    canalData.getDatabase(), canalData.getTable(), type, index);
            return GXCanalParseResultInnerDto.skipped("ddl event: " + type, index);
        }

        if (CharSequenceUtil.isBlank(canalData.getDatabase()) || CharSequenceUtil.isBlank(canalData.getTable())) {
            logThrottle.warn("missing-location", "Database or table is blank: index={}", index);
            return GXCanalParseResultInnerDto.nonRetryableFailure(REASON_MISSING_LOCATION, index);
        }

        GXProcessCanalDataService handler = handlerResolver.resolve(canalData.getDatabase(), canalData.getTable());
        if (handler == null) {
            logThrottle.error("no-handler", "No handler bean and no default handler: database={}, table={}, index={}",
                    canalData.getDatabase(), canalData.getTable(), index);
            return GXCanalParseResultInnerDto.nonRetryableFailure(REASON_NO_HANDLER, index);
        }

        String operation;
        Dict handlerResult;
        switch (type) {
            case GXCanalConstant.TYPE_INSERT -> {
                operation = GXCanalConstant.OPERATOR_INSERT;
                handlerResult = handler.processInsert(canalData, operatorParam(operation));
            }
            case GXCanalConstant.TYPE_UPDATE -> {
                operation = GXCanalConstant.OPERATOR_UPDATE;
                handlerResult = handler.processUpdate(canalData, operatorParam(operation));
            }
            case GXCanalConstant.TYPE_DELETE -> {
                operation = GXCanalConstant.OPERATOR_DELETE;
                handlerResult = handler.processDelete(canalData, operatorParam(operation));
            }
            default -> {
                logThrottle.warn("unknown-type:" + type, "Unknown operation type: type={}, index={}", type, index);
                return GXCanalParseResultInnerDto.nonRetryableFailure("unknown operation type: " + type, index);
            }
        }

        if (handlerResult == null) {
            logThrottle.warn("handler-null", "Handler returned null: database={}, table={}, operation={}, index={}",
                    canalData.getDatabase(), canalData.getTable(), operation, index);
            return GXCanalParseResultInnerDto.nonRetryableFailure(REASON_HANDLER_NULL, index);
        }
        log.debug("Dispatched {}: database={}, table={}, index={}",
                operation, canalData.getDatabase(), canalData.getTable(), index);
        return GXCanalParseResultInnerDto.success(canalData, operation, handlerResult, index);
    }

    private Dict operatorParam(String operation) {
        return Dict.create().set(GXCanalConstant.PARAM_OPERATOR, operation);
    }
}
