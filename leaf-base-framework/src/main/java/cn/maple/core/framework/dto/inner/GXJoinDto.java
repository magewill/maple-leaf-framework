package cn.maple.core.framework.dto.inner;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
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

    public void setAnd(List<GXDbJoinOp> and) {
        fillJoinAliases(and);
        this.and = and;
    }

    public void setOr(List<GXDbJoinOp> or) {
        fillJoinAliases(or);
        this.or = or;
    }

    private void fillJoinAliases(List<GXDbJoinOp> ops) {
        for (GXDbJoinOp op : ops == null ? Collections.<GXDbJoinOp>emptyList() : ops) {
            if (op == null) {
                continue;
            }
            if (CharSequenceUtil.isEmpty(op.getMasterTableNameAlias())) {
                op.setMasterTableNameAlias(masterTableNameAlias);
            }
            if (CharSequenceUtil.isEmpty(op.getJoinTableNameAlias())) {
                op.setJoinTableNameAlias(joinTableNameAlias);
            }
        }
    }
}
