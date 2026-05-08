package cn.maple.core.framework.dto;

import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXConditionJsonEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionLikeFull;
import cn.maple.core.framework.dto.inner.condition.GXConditionLikeLeft;
import cn.maple.core.framework.dto.inner.condition.GXConditionLikeRight;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrNE;
import cn.maple.core.framework.dto.inner.field.GXUpdateRawField;
import cn.maple.core.framework.dto.inner.op.GXDbJoinEQ;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DtoBehaviorTest {
    @Test
    void paginationCalculatesPagesAndHandlesInvalidPageSize() {
        GXPaginationResDto<String> page = new GXPaginationResDto<>(List.of("a", "b"), 21, 10, 1);
        GXPaginationResDto<String> invalidPageSize = new GXPaginationResDto<>(List.of("a"), 21, 0, 1);

        assertEquals(3, page.getPages());
        assertEquals(0, invalidPageSize.getPages());
    }

    @Test
    void joinDtoFillsMissingAliasesAndKeepsNullCollectionSafe() {
        GXDbJoinEQ op = new GXDbJoinEQ("id", "userId");
        List<GXDbJoinOp> ops = new ArrayList<>();
        ops.add(null);
        ops.add(op);

        GXJoinDto joinDto = GXJoinDto.builder()
                .masterTableNameAlias("m")
                .joinTableNameAlias("j")
                .build();

        joinDto.setAnd(ops);
        joinDto.setOr(null);

        assertEquals("m", op.getMasterTableNameAlias());
        assertEquals("j", op.getJoinTableNameAlias());
        assertEquals(ops, joinDto.getAnd());
        assertNull(joinDto.getOr());
    }

    @Test
    void rawUpdateFieldBuildsSqlAndRejectsInjectionValue() {
        GXUpdateRawField rawField = new GXUpdateRawField("u", "score", "score + 1");

        assertEquals("score + 1", rawField.getFieldValue());
        assertEquals("u.score = score + 1", rawField.updateString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateRawField("u", "score", "1;drop table user").getFieldValue());
    }

    @Test
    void stringConditionsRejectNullValueExplicitly() {
        assertThrows(GXBusinessException.class, () -> new GXConditionStrEQ("u", "name", null).getFieldValue());
        assertThrows(GXBusinessException.class, () -> new GXConditionStrNE("u", "name", null).getFieldValue());
        assertThrows(GXBusinessException.class, () -> new GXConditionLikeFull("u", "name", null).getFieldValue());
        assertThrows(GXBusinessException.class, () -> new GXConditionLikeLeft("u", "name", null).getFieldValue());
        assertThrows(GXBusinessException.class, () -> new GXConditionLikeRight("u", "name", null).getFieldValue());
    }

    @Test
    void jsonConditionRejectsNullValueConsistently() {
        GXConditionJsonEQ condition = new GXConditionJsonEQ("u", "extra", "status", null);

        assertThrows(GXBusinessException.class, condition::getFieldValue);
        assertThrows(GXBusinessException.class, condition::getFieldOriginalValue);
        assertThrows(GXBusinessException.class, condition::whereString);
        assertThrows(GXBusinessException.class, condition::toSegment);
    }

    @Test
    void rawUpdateFieldRejectsNullValueExplicitly() {
        assertThrows(GXBusinessException.class, () -> new GXUpdateRawField("u", "score", null).getFieldValue());
    }

    @Test
    void joinTypeDescriptionsUseAsciiText() {
        assertEquals("LEFT JOIN", GXJoinTypeEnums.LEFT.getDesc());
        assertEquals("RIGHT JOIN", GXJoinTypeEnums.RIGHT.getDesc());
        assertEquals("INNER JOIN", GXJoinTypeEnums.INNER.getDesc());
    }
}
