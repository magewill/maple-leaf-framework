package cn.maple.core.framework.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXAuthCodeUtilsTest {
    private static final String KEY = "secret-key";

    @Test
    void authCodeEncodeAndDecodeRoundTripPlainText() {
        String token = GXAuthCodeUtils.authCodeEncode("hello", KEY, 60);

        assertEquals("hello", GXAuthCodeUtils.authCodeDecode(token, KEY));
    }

    @Test
    void authCodeEncodeAndDecodeRoundTripJsonText() {
        String json = "{\"name\":\"alice\",\"roles\":[\"admin\",\"user\"]}";
        String token = GXAuthCodeUtils.authCodeEncode(json, KEY);

        assertEquals(json, GXAuthCodeUtils.authCodeDecode(token, KEY));
    }

    @Test
    void authCodeDecodeReturnsEmptyJsonForWrongKeyOrMalformedToken() {
        String token = GXAuthCodeUtils.authCodeEncode("hello", KEY, 60);

        assertEquals("{}", GXAuthCodeUtils.authCodeDecode(token, "wrong-key"));
        assertEquals("{}", GXAuthCodeUtils.authCodeDecode("not-a-token", KEY));
        assertEquals("{}", GXAuthCodeUtils.authCodeDecode("abcd", KEY));
        assertEquals("{}", GXAuthCodeUtils.authCodeDecode(null, KEY));
    }

    @Test
    void authCodeEncodeReturnsEmptyJsonForEmptySourceOrKey() {
        assertEquals("{}", GXAuthCodeUtils.authCodeDecode(GXAuthCodeUtils.authCodeEncode("", KEY, 60), KEY));
        assertEquals("{}", GXAuthCodeUtils.authCodeDecode(GXAuthCodeUtils.authCodeEncode("hello", "", 60), KEY));
        assertEquals("{}", GXAuthCodeUtils.authCodeDecode(GXAuthCodeUtils.authCodeEncode(null, KEY, 60), KEY));
    }

    @Test
    void authCodeDecodeReturnsEmptyJsonWhenExpired() throws InterruptedException {
        String token = GXAuthCodeUtils.authCodeEncode("hello", KEY, 1);

        Thread.sleep(1200);

        assertEquals("{}", GXAuthCodeUtils.authCodeDecode(token, KEY));
    }

    @Test
    void authCodeEncodeUsesRandomPrefixForDifferentTokens() {
        String first = GXAuthCodeUtils.authCodeEncode("hello", KEY, 60);
        String second = GXAuthCodeUtils.authCodeEncode("hello", KEY, 60);

        assertNotEquals(first, second);
        assertEquals("hello", GXAuthCodeUtils.authCodeDecode(first, KEY));
        assertEquals("hello", GXAuthCodeUtils.authCodeDecode(second, KEY));
    }

    @Test
    void authCodeEncodeSupportsLargeExpiryWithoutIntegerOverflow() {
        String token = GXAuthCodeUtils.authCodeEncode("hello", KEY, Integer.MAX_VALUE);

        assertEquals("hello", GXAuthCodeUtils.authCodeDecode(token, KEY));
    }

    @Test
    void authCodeEncodeAndDecodeAreSafeForConcurrentCalls() throws Exception {
        int taskCount = 64;
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>(taskCount);
            for (int i = 0; i < taskCount; i++) {
                final int index = i;
                tasks.add(() -> {
                    String value = "payload-" + index;
                    String token = GXAuthCodeUtils.authCodeEncode(value, KEY, 60);
                    return value.equals(GXAuthCodeUtils.authCodeDecode(token, KEY));
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
    void fileExistsHandlesNullMissingAndExistingFiles(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("auth-code.txt"));

        assertFalse(GXAuthCodeUtils.fileExists(null));
        assertFalse(GXAuthCodeUtils.fileExists(tempDir.resolve("missing.txt").toString()));
        assertTrue(GXAuthCodeUtils.fileExists(file.toString()));
    }

    @Test
    void strIsNullOrEmptyPreservesTrimBasedBehavior() {
        assertTrue(GXAuthCodeUtils.strIsNullOrEmpty(null));
        assertTrue(GXAuthCodeUtils.strIsNullOrEmpty(""));
        assertTrue(GXAuthCodeUtils.strIsNullOrEmpty("   "));
        assertFalse(GXAuthCodeUtils.strIsNullOrEmpty(" value "));
    }
}
