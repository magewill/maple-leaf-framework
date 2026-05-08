package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXTokenInvalidException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXTokenManagerUtilsTest {
    private static final String SECRET = "secret-key";

    @Test
    void generateAndDecodeUserTokenAddsDefaultFields() {
        Dict param = Dict.create().set(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, "alice");

        String token = GXTokenManagerUtils.generateUserToken(7L, param, SECRET, 60);
        Dict decoded = GXTokenManagerUtils.decodeUserToken(token, SECRET);

        assertEquals("alice", decoded.getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME));
        assertEquals(7, decoded.getInt(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        assertEquals(GXTokenConstant.PLATFORM, decoded.getStr("platform"));
        assertTrue(decoded.containsKey(GXTokenConstant.LOGIN_AT_FIELD_NAME));
    }

    @Test
    void generateAndDecodeManagerTokenPreservesExistingFields() {
        Dict param = Dict.create()
                .set(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, "admin")
                .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, 99)
                .set("platform", "custom");

        String token = GXTokenManagerUtils.generateManagerToken(7L, param, SECRET, 60);
        Dict decoded = GXTokenManagerUtils.decodeManagerToken(token, SECRET);

        assertEquals(99, decoded.getInt(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        assertEquals("custom", decoded.getStr("platform"));
    }

    @Test
    void generateTokenDoesNotMutateCallerParam() {
        Dict param = Dict.create().set(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, "alice");

        GXTokenManagerUtils.generateUserToken(7L, param, SECRET, 60);

        assertFalse(param.containsKey(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        assertFalse(param.containsKey(GXTokenConstant.LOGIN_AT_FIELD_NAME));
        assertFalse(param.containsKey("platform"));
    }

    @Test
    void generateAndDecodeTokenAreSafeForConcurrentCalls() throws Exception {
        Dict param = Dict.create().set(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, "alice");
        int taskCount = 64;
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>(taskCount);
            for (int i = 0; i < taskCount; i++) {
                final int userId = i;
                tasks.add(() -> {
                    String token = GXTokenManagerUtils.generateUserToken(userId, param, SECRET, 60);
                    Dict decoded = GXTokenManagerUtils.decodeUserToken(token, SECRET);
                    return decoded.getInt(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME) == userId
                            && "alice".equals(decoded.getStr(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME));
                });
            }

            List<Future<Boolean>> futures = executorService.invokeAll(tasks);

            for (Future<Boolean> future : futures) {
                assertTrue(future.get());
            }
        } finally {
            executorService.close();
        }
    }

    @Test
    void generateTokenRejectsInvalidArguments() {
        assertThrows(GXBusinessException.class, () -> GXTokenManagerUtils.generateUserToken(1, null, SECRET, 60));
        assertThrows(GXBusinessException.class, () -> GXTokenManagerUtils.generateUserToken(1, Dict.create(), SECRET, 60));
        assertThrows(GXBusinessException.class, () -> GXTokenManagerUtils.generateUserToken(1,
                Dict.create().set(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, "alice"), "", 60));
        assertThrows(GXBusinessException.class, () -> GXTokenManagerUtils.generateUserToken(1,
                Dict.create().set(GXTokenConstant.TOKEN_USER_NAME_FIELD_NAME, "alice"), SECRET, 0));
    }

    @Test
    void decodeTokenRejectsInvalidArgumentsAndMalformedData() {
        assertThrows(GXTokenInvalidException.class, () -> GXTokenManagerUtils.decodeUserToken("", SECRET));
        assertThrows(GXTokenInvalidException.class, () -> GXTokenManagerUtils.decodeUserToken("token", ""));
        assertThrows(GXTokenInvalidException.class, () -> GXTokenManagerUtils.decodeUserToken("not-a-token", SECRET));
        assertThrows(GXTokenInvalidException.class, () -> GXTokenManagerUtils.decodeUserToken(
                GXAuthCodeUtils.authCodeEncode("[]", SECRET, 60), SECRET));
    }

    @Test
    void verifyTokenEffectivenessReturnsTrueWhenSsoModuleUnavailable() {
        assertTrue(GXTokenManagerUtils.verifyTokenEffectiveness());
    }
}
