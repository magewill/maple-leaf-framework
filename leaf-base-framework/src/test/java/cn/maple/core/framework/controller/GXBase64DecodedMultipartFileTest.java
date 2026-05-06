package cn.maple.core.framework.controller;

import cn.maple.core.framework.exception.GXBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXBase64DecodedMultipartFileTest {
    @TempDir
    Path tempDir;

    @Test
    void decodesDataUriAndProvidesSafeMetadata() throws Exception {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        String source = "data:text/plain;base64," + Base64.getEncoder().encodeToString(payload);

        GXBase64DecodedMultipartFile file = new GXBase64DecodedMultipartFile(source);

        assertEquals("base64.plain", file.getName());
        assertEquals("base64.plain", file.getOriginalFilename());
        assertEquals("text/plain", file.getContentType());
        assertEquals(payload.length, file.getSize());
        assertFalse(file.isEmpty());
        assertArrayEquals(payload, file.getBytes());
    }

    @Test
    void getBytesReturnsDefensiveCopy() throws Exception {
        GXBase64DecodedMultipartFile file = new GXBase64DecodedMultipartFile(
                "data:text/plain;base64," + Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8)));

        byte[] bytes = file.getBytes();
        bytes[0] = 'z';

        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), file.getBytes());
    }

    @Test
    void transferToCreatesParentDirectories() throws Exception {
        byte[] payload = "content".getBytes(StandardCharsets.UTF_8);
        GXBase64DecodedMultipartFile file = new GXBase64DecodedMultipartFile(
                "data:text/plain;base64," + Base64.getEncoder().encodeToString(payload));
        Path destination = tempDir.resolve("nested/file.txt");

        file.transferTo(destination);

        assertArrayEquals(payload, Files.readAllBytes(destination));
    }

    @Test
    void rejectsInvalidBase64Input() {
        assertThrows(GXBusinessException.class, () -> new GXBase64DecodedMultipartFile(""));
        assertThrows(GXBusinessException.class, () -> new GXBase64DecodedMultipartFile("abc"));
        assertThrows(GXBusinessException.class, () -> new GXBase64DecodedMultipartFile("data:text/plain;base64,"));
        assertThrows(GXBusinessException.class, () -> new GXBase64DecodedMultipartFile("data:text/plain;base64,not base64"));
    }
}
