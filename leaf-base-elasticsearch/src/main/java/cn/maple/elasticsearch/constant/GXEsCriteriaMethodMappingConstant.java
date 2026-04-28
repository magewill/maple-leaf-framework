package cn.maple.elasticsearch.constant;

import java.util.LinkedHashMap;
import java.util.Map;

public class GXEsCriteriaMethodMappingConstant {
    public static final Map<String, String> METHOD_MAPPING = createMethodMapping();

    private GXEsCriteriaMethodMappingConstant() {

    }

    private static Map<String, String> createMethodMapping() {
        Map<String, String> methodMapping = new LinkedHashMap<>();
        methodMapping.put("=", "is");
        methodMapping.put("!=", "not().is");
        methodMapping.put("in", "in");
        methodMapping.put("not in", "notIn");
        methodMapping.put(">", "greaterThan");
        methodMapping.put("<", "lessThan");
        methodMapping.put(">=", "greaterThanEqual");
        methodMapping.put("<=", "lessThanEqual");
        methodMapping.put("like", "fuzzy");
        methodMapping.put("between", "between");
        methodMapping.put("is", "not().exists");
        methodMapping.put("is not", "exists");
        return Map.copyOf(methodMapping);
    }
}
