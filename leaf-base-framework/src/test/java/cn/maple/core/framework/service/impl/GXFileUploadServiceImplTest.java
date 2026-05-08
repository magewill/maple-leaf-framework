package cn.maple.core.framework.service.impl;

import cn.maple.core.framework.controller.GXBase64DecodedMultipartFile;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXFileUploadServiceImplTest {
    @TempDir
    Path tempDir;

    private final GXFileUploadServiceImpl service = new GXFileUploadServiceImpl();

    @Test
    void uploadMultipartFileStoresFileUnderRelativePath() throws Exception {
        try (MockedStatic<GXCommonUtils> commonUtils = mockStorageRoot()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "hello.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

            String filename = service.upload("docs", file);

            assertTrue(filename.endsWith(".txt"));
            assertTrue(Files.exists(tempDir.resolve("docs").resolve(filename)));
            assertEquals("hello", Files.readString(tempDir.resolve("docs").resolve(filename)));
            assertEquals(tempDir.resolve("docs").toAbsolutePath().toString(), service.getStoragePath());
        }
    }

    @Test
    void uploadBase64FileStoresDecodedContent() throws Exception {
        try (MockedStatic<GXCommonUtils> commonUtils = mockStorageRoot()) {
            GXBase64DecodedMultipartFile file = new GXBase64DecodedMultipartFile("data:text/plain;base64,aGVsbG8=");

            String filename = service.upload("base64", file);

            assertTrue(filename.endsWith(".plain"));
            assertEquals("hello", Files.readString(tempDir.resolve("base64").resolve(filename)));
        }
    }

    @Test
    void uploadRejectsEmptyFileAndUnsafeRelativePath() {
        try (MockedStatic<GXCommonUtils> commonUtils = mockStorageRoot()) {
            MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
            MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

            assertThrows(GXBusinessException.class, () -> service.upload("docs", emptyFile));
            assertThrows(GXBusinessException.class, () -> service.upload("../outside", file));
            assertThrows(GXBusinessException.class, () -> service.upload("/absolute", file));
        }
    }

    @Test
    void deleteFileDeletesOnlySafeFileNames() throws Exception {
        try (MockedStatic<GXCommonUtils> commonUtils = mockStorageRoot()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "hello.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
            String filename = service.upload("docs", file);
            Path storedFile = tempDir.resolve("docs").resolve(filename);

            assertTrue(Files.exists(storedFile));
            assertTrue(service.deleteFile(filename));
            assertFalse(Files.exists(storedFile));
            assertTrue(service.deleteFile(filename));
            assertThrows(GXBusinessException.class, () -> service.deleteFile("../" + filename));
            assertThrows(GXBusinessException.class, () -> service.deleteFile(""));
        }
    }

    @Test
    void concurrentUploadsDoNotShareDestinationDirectoryState() throws Exception {
        List<Callable<Path>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int index = i;
            tasks.add(() -> {
                try (MockedStatic<GXCommonUtils> commonUtils = mockStorageRoot()) {
                    String relativePath = "dir" + index;
                    MockMultipartFile file = new MockMultipartFile(
                            "file", "file" + index + ".dat", "application/octet-stream",
                            ("data" + index).getBytes(StandardCharsets.UTF_8));
                    String filename = service.upload(relativePath, file);
                    return tempDir.resolve(relativePath).resolve(filename);
                }
            });
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Path>> futures = executor.invokeAll(tasks);
            for (Future<Path> future : futures) {
                Path uploaded = future.get();
                assertNotNull(uploaded.getFileName());
                assertTrue(Files.exists(uploaded));
            }
        }
    }

    private MockedStatic<GXCommonUtils> mockStorageRoot() {
        MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class);
        commonUtils.when(() -> GXCommonUtils.getEnvironmentValue("upload.depositPath", String.class, "./Uploads/files"))
                .thenReturn(tempDir.toString());
        return commonUtils;
    }
}
