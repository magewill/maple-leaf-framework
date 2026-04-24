package cn.maple.core.framework.dto.inner.condition;

import cn.maple.core.framework.constant.GXDataSourceConstant;

public class GXIgnoreDataFilterCondition extends GXCondition<String> {
    public GXIgnoreDataFilterCondition(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    public GXIgnoreDataFilterCondition() {
        this("", "", "");
    }

    @Override
    public String getOp() {
        return GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE;
    }

    @Override
    public String getFieldValue() {
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        return "";
    }
}
