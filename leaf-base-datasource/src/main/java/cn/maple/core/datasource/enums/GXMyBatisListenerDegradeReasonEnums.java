package cn.maple.core.datasource.enums;

import lombok.Getter;

/**
 * Reasons why a listener payload could not be fully reconstructed.
 */
@Getter
public enum GXMyBatisListenerDegradeReasonEnums {
    OR_EXPRESSION("条件包含 OR，无法还原为字段到值的单值映射"),

    NON_COLUMN_LEFT("操作符左侧不是列，可能是函数、表达式或子查询"),

    DUPLICATE_FIELD("同一字段重复出现，无法唯一还原"),

    PARAM_MISMATCH("占位符数量或名称与解析出的条件不匹配"),

    PARSE_ERROR("条件文本解析失败"),

    NON_ABSTRACT_WRAPPER("条件对象不是可解析的标准包装器"),

    UNSNAPSHOTABLE_VALUE("取值不可克隆，只能保留原始引用");

    private final String desc;

    GXMyBatisListenerDegradeReasonEnums(String desc) {
        this.desc = desc;
    }
}
