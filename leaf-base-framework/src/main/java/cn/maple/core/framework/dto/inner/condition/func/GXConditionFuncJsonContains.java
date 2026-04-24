package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class GXConditionFuncJsonContains extends GXConditionFunc<String> {
    private final Object values;
    private final String jsonField;
    private final String rawJsonPath;

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
        super(tableNameAlias, jsonField, "");
        this.values = values;
        this.jsonField = jsonField;
        this.rawJsonPath = jsonPath;
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values, String jsonPath) {
        super(tableNameAlias, jsonField, "");
        this.values = values;
        this.jsonField = jsonField;
        this.rawJsonPath = jsonPath;
    }

    @Override
    public String getOp() {
        return jsonField;
    }

    @Override
    public String getFieldExpression() {
        this.paramMap.put(paramName + "_path", normalizeJsonPath(rawJsonPath));
        String format = "`{}`.`{}`->#{dbQueryParamInnerDto.paramMap.{}_path}, CAST(#{dbQueryParamInnerDto.paramMap.{}} AS JSON)";
        return CharSequenceUtil.format(format, tableNameAlias, jsonField, paramName, paramName);
    }

    @Override
    public String getFieldValue() {
        if (values == null) {
            throw new GXBusinessException("JSON_CONTAINS value must not be null");
        }
        if (values instanceof Dict) {
            return JSONUtil.toJsonStr(values);
        }
        if (values instanceof List<?>) {
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw new GXBusinessException("ObjectMapper bean is required for JSON_CONTAINS");
            }
            try {
                return objectMapper.writeValueAsString(values);
            } catch (JacksonException e) {
                throw new GXBusinessException(e.getMessage(), e);
            }
        }
        return values.toString();
    }

    @Override
    protected String getFunctionName() {
        return "JSON_CONTAINS";
    }

    @Override
    public String whereString() {
        this.paramMap.clear();
        this.paramMap.put(paramName, getFieldValue());
        return CharSequenceUtil.format("{}({})", getFunctionName(), getFieldExpression());
    }

    @Override
    public String getFieldOriginalValue() {
        return "";
    }

    private static String normalizeJsonPath(String jsonPath) {
        if (CharSequenceUtil.isBlank(jsonPath)) {
            return "$";
        }
        String trimmed = CharSequenceUtil.trim(jsonPath);
        if ("$".equals(trimmed) || trimmed.startsWith("$.") || trimmed.startsWith("$[")) {
            return trimmed;
        }
        if (trimmed.startsWith(".")) {
            return "$" + trimmed;
        }
        if (trimmed.startsWith("$")) {
            return "$." + trimmed.substring(1);
        }
        return "$." + trimmed;
    }
}
