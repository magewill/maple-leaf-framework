package cn.maple.core.framework.dto;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.GXJoinDto;
import cn.maple.core.framework.dto.inner.GXJoinTypeEnums;
import cn.maple.core.framework.dto.inner.GXUnionTypeEnums;
import cn.maple.core.framework.dto.inner.condition.GXConditionJsonEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionLikeFull;
import cn.maple.core.framework.dto.inner.condition.GXConditionLikeLeft;
import cn.maple.core.framework.dto.inner.condition.GXConditionLikeRight;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrEQ;
import cn.maple.core.framework.dto.inner.condition.GXConditionStrNE;
import cn.maple.core.framework.dto.inner.field.GXUpdateJsonSetStrField;
import cn.maple.core.framework.dto.inner.field.GXUpdateRawField;
import cn.maple.core.framework.dto.inner.field.GXUpdateStrField;
import cn.maple.core.framework.dto.inner.op.GXDbJoinEQ;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import cn.maple.core.framework.dto.inner.op.GXDbJoinValueEQ;
import cn.maple.core.framework.dto.protocol.req.GXQueryParamReqProtocol;
import cn.maple.core.framework.dto.protocol.res.GXPaginationResProtocol;
import cn.maple.core.framework.dto.res.GXPaginationResDto;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoBehaviorTest {
    @Test
    void paginationCalculatesPagesAndHandlesInvalidPageSize() {
        GXPaginationResDto<String> page = new GXPaginationResDto<>(List.of("a", "b"), 21, 10, 1);
        GXPaginationResDto<String> invalidPageSize = new GXPaginationResDto<>(List.of("a"), 21, 0, 1);

        assertEquals(3, page.getPages());
        assertEquals(0, invalidPageSize.getPages());
    }

    @Test
    void paginationCopiesRecordsAndExposesReadOnlyView() {
        List<String> source = new ArrayList<>(List.of("a"));
        GXPaginationResDto<String> page = new GXPaginationResDto<>(source, 1, 10, 1);

        source.add("b");

        assertEquals(List.of("a"), page.getRecords());
        assertThrows(UnsupportedOperationException.class, () -> page.getRecords().add("c"));
    }

    @Test
    void paginationProtocolCopiesRecordsAndKeepsGetterMutable() {
        List<String> source = new ArrayList<>(List.of("a"));
        GXPaginationResProtocol<String> protocol = new GXPaginationResProtocol<>(source, 1, 1, 10, 1);

        source.add("b");
        protocol.getRecords().add("c");

        assertEquals(List.of("a", "c"), protocol.getRecords());
    }

    @Test
    void baseEventKeepsSourceIdentityAndCopiesConstructorParam() {
        Object source = new Object();
        Dict param = Dict.create().set("name", "before");
        GXBaseEvent<Object> event = new GXBaseEvent<>(source, "type", param, "name");

        param.set("name", "after");

        assertSame(source, event.getSource());
        assertEquals("before", event.getParam().getStr("name"));
    }

    @Test
    void queryParamReqProtocolCopiesCollectionsAndNormalizesPageValues() {
        GXQueryParamReqProtocol protocol = new GXQueryParamReqProtocol();
        Map<String, String> orderBy = new LinkedHashMap<>();
        orderBy.put("name", "ASC");
        Set<String> columns = new LinkedHashSet<>();
        columns.add("id");

        protocol.setPage(0);
        protocol.setPageSize(-1);
        protocol.setOrderByField(orderBy);
        protocol.setColumns(columns);
        orderBy.put("created_at", "DESC");
        columns.add("name");

        assertEquals(GXCommonConstant.DEFAULT_CURRENT_PAGE, protocol.getPage());
        assertEquals(GXCommonConstant.DEFAULT_PAGE_SIZE, protocol.getPageSize());
        assertEquals(Map.of("name", "ASC"), protocol.getOrderByField());
        assertEquals(Set.of("id"), protocol.getColumns());
        assertThrows(UnsupportedOperationException.class, () -> protocol.getColumns().add("name"));
    }

    @Test
    void joinDtoDoesNotMutateJoinOpsWhenSettingCollections() {
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

        assertNull(op.getMasterTableNameAlias());
        assertNull(op.getJoinTableNameAlias());
        assertEquals(ops, joinDto.getAnd());
        assertNull(joinDto.getOr());
    }

    @Test
    void joinOpsRejectUnsafeIdentifiersAndEscapeLiteralValues() {
        GXDbJoinEQ op = new GXDbJoinEQ("user.id", "role.userId");
        op.setMasterTableNameAlias("u");
        op.setJoinTableNameAlias("r");
        assertEquals("u.id=r.userId", op.opString());

        assertThrows(GXSqlInjectionException.class, () -> new GXDbJoinEQ("id;drop", "userId").opString());
        assertThrows(GXSqlInjectionException.class, () -> {
            GXDbJoinEQ unsafeAlias = new GXDbJoinEQ("id", "userId");
            unsafeAlias.setMasterTableNameAlias("u;drop");
            unsafeAlias.opString();
        });

        GXDbJoinValueEQ valueOp = new GXDbJoinValueEQ("u", "name", "O'Reilly");
        assertEquals("u.name='O''Reilly'", valueOp.opString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXDbJoinValueEQ("u", "name", "x' or '1'='1").opString());
    }

    @Test
    void rawUpdateFieldBuildsSqlAndRejectsInjectionValue() {
        GXUpdateRawField rawField = new GXUpdateRawField("u", "score", "score + 1");

        assertEquals("score + 1", rawField.getFieldValue());
        assertEquals("u.score = score + 1", rawField.updateString());
        assertEquals("u.score = case when status = 1 or status = 2 then score else 0 end",
                new GXUpdateRawField("u", "score", "case when status = 1 or status = 2 then score else 0 end").updateString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateRawField("u", "score", "1;drop table user").getFieldValue());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateRawField("u", "score", "1;drop table user").updateString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateRawField("u;drop", "score", "score + 1").updateString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateRawField("u", "score;drop", "score + 1").updateString());
    }

    @Test
    void jsonUpdateFieldRejectsUnsafeIdentifiers() {
        GXUpdateJsonSetStrField field = new GXUpdateJsonSetStrField("u", "extraData", "status", "active");
        assertTrue(field.updateString().startsWith("u.extra_data = JSON_SET(u.extra_data, "));
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateJsonSetStrField("u;drop", "extraData", "status", "active").updateString());
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
    void conditionsRejectUnsafeIdentifiers() {
        assertThrows(GXSqlInjectionException.class,
                () -> new GXConditionStrEQ("u;drop", "name", "maple").whereString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXConditionStrEQ("u", "name;drop", "maple").whereString());
        GXConditionStrEQ condition = new GXConditionStrEQ("u", "name", "maple");
        assertThrows(GXSqlInjectionException.class, () -> condition.setTableNameAlias("x;drop"));
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
    void regularUpdateFieldsRejectUnsafeIdentifiers() {
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateStrField("u;drop", "name", "maple").updateString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateStrField("u", "name;drop", "maple").updateString());
        assertThrows(GXSqlInjectionException.class,
                () -> new GXUpdateStrField("u;drop", "name", null).updateString());
    }

    @Test
    void joinTypeDescriptionsUseAsciiText() {
        assertEquals("LEFT JOIN", GXJoinTypeEnums.LEFT.getDesc());
        assertEquals("RIGHT JOIN", GXJoinTypeEnums.RIGHT.getDesc());
        assertEquals("INNER JOIN", GXJoinTypeEnums.INNER.getDesc());
    }

    @Test
    void unionTypesUseUppercaseSqlKeywords() {
        assertEquals("UNION", GXUnionTypeEnums.UNION.getUnionType());
        assertEquals("UNION ALL", GXUnionTypeEnums.UNION_ALL.getUnionType());
    }
}
