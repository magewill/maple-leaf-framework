package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GXJoinDto {
    private String joinTableName;

    private String joinTableNameAlias;

    private String masterTableNameAlias;

    private String masterTableName;

    private GXJoinTypeEnums joinType;

    private List<GXDbJoinOp> and;

    private List<GXDbJoinOp> or;

    private List<GXCondition<?>> conditions;

    private boolean autoFillIsDeleteCondition;
}
