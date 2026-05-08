package cn.maple.core.framework.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXBusinessServiceImplTest {
    private final GXBusinessServiceImpl service = new GXBusinessServiceImpl();

    @Test
    void phoneEncryptionRejectsEmptyKey() {
        assertThrows(GXBusinessException.class, () -> service.encryptedPhoneNumber("13800138000", ""));
        assertThrows(GXBusinessException.class, () -> service.decryptedPhoneNumber("encrypted", ""));
    }

    @Test
    void getSingleFieldValueByEntityReturnsDefaultForNullOrMissingValue() {
        Dict entity = Dict.create().set("name", "maple");

        assertEquals("fallback", service.getSingleFieldValueByEntity(null, "name", String.class, "fallback"));
        assertEquals("fallback", service.getSingleFieldValueByEntity(entity, "", String.class, "fallback"));
        assertEquals("fallback", service.getSingleFieldValueByEntity(entity, "missing", String.class, "fallback"));
        assertEquals("fallback", service.getSingleFieldValueByEntity(entity, "name", null, "fallback"));
    }

    @Test
    void getSingleFieldValueByEntityReadsNestedPath() {
        Dict entity = Dict.create()
                .set("profile", Dict.create().set("age", 18))
                .set("payload", Dict.create().set("name", "maple"));

        assertEquals(18, service.getSingleFieldValueByEntity(entity, "profile::age", Integer.class, 0));
        assertEquals(Dict.create().set("name", "maple"),
                service.getSingleFieldValueByEntity(entity, "payload", Dict.class, Dict.create()));
    }

    @Test
    void getSingleFieldValueByEntityThrowsWhenMainFieldIsMissing() {
        Dict entity = Dict.create().set("profile", Dict.create().set("age", 18));

        assertThrows(GXBusinessException.class,
                () -> service.getSingleFieldValueByEntity(entity, "missing::age", Integer.class, 0));
    }

    @Test
    void convertSourceToTargetReturnsNullForNullSource() {
        assertNull(service.convertSourceToTarget(null, TargetDto.class));
    }

    public static class TargetDto {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
