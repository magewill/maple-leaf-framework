package cn.maple.core.framework.filter;

import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXSQLFilterTest {
    @Test
    void sqlInjectPreservesCaseAndDoesNotMatchKeywordSubstrings() {
        assertEquals("NormalOrderValue", GXSQLFilter.sqlInject("NormalOrderValue"));
    }

    @Test
    void sqlInjectRemovesDangerousDelimitersWithoutLowercasing() {
        assertEquals("ABC", GXSQLFilter.sqlInject("'A\"B;C\\"));
    }

    @Test
    void sqlInjectRejectsKeywordTokens() {
        assertThrows(GXBusinessException.class, () -> GXSQLFilter.sqlInject("1 union select 1"));
        assertThrows(GXBusinessException.class, () -> GXSQLFilter.sqlInject("name or 1=1"));
        assertThrows(GXBusinessException.class, () -> GXSQLFilter.sqlInject("name--"));
    }

    @Test
    void sqlInjectReturnsNullForBlankInput() {
        assertNull(GXSQLFilter.sqlInject(""));
        assertNull(GXSQLFilter.sqlInject(" "));
        assertNull(GXSQLFilter.sqlInject(null));
    }
}
