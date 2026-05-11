package cn.maple.core.framework.dto.inner.op;

import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXDbJoinOpTest {
    @Test
    void fieldJoinOpsRenderExpectedOperators() {
        assertEquals("m.id!=j.id", withAliases(new GXDbJoinNE("id", "id")));
        assertEquals("m.id>j.id", withAliases(new GXDbJoinGT("id", "id")));
        assertEquals("m.id<j.id", withAliases(new GXDbJoinLT("id", "id")));
        assertEquals("m.id<=j.id", withAliases(new GXDbJoinLE("id", "id")));
    }

    @Test
    void valueJoinOpsRenderExpectedSqlAndParams() {
        GXConditionSegment gt = new GXDbJoinValueGT("u", "score", 10).toSegment("join_1");
        GXConditionSegment lt = new GXDbJoinValueLT("u", "score", 10).toSegment("join_2");
        GXConditionSegment ne = new GXDbJoinValueNE("u", "score", 10).toSegment("join_3");

        assertEquals("u.score>#{dbQueryParamInnerDto.paramMap.join_1}", gt.sql());
        assertEquals(10, gt.params().get("join_1"));
        assertEquals("u.score<#{dbQueryParamInnerDto.paramMap.join_2}", lt.sql());
        assertEquals(10, lt.params().get("join_2"));
        assertEquals("u.score!=#{dbQueryParamInnerDto.paramMap.join_3}", ne.sql());
        assertEquals(10, ne.params().get("join_3"));
    }

    private static String withAliases(GXDbJoinOp op) {
        op.setMasterTableNameAlias("m");
        op.setJoinTableNameAlias("j");
        return op.opString();
    }
}
