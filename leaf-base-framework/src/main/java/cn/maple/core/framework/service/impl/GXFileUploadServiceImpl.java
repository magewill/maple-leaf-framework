package cn.maple.core.framework.service.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.controller.GXBase64DecodedMultipartFile;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXFileUploadService;
import cn.maple.core.framework.util.GXCommonUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Log4j2
@Service
@ConditionalOnExpression("'${maple.framework.enable.file-upload}'.equals('true')")
public class GXFileUploadServiceImpl implements GXFileUploadService {
    private static final String DEFAULT_STORAGE_PATH = "./Uploads/files";

    private volatile Path fileStoragePath;

    @Override
    public String upload(String relativePath, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GXBusinessException("File must not be empty");
        }
        Path storageDirectory = resolveStorageDirectory(relativePath);
        Path destinationFile = resolveUploadDestination(storageDirectory, getSafeFileExtension(file.getOriginalFilename()));
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new GXBusinessException("Failed to save file", ex);
        }
        fileStoragePath = storageDirectory;
        return destinationFile.getFileName().toString();
    }

    @Override
    public String upload(String relativePath, GXBase64DecodedMultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GXBusinessException("File must not be empty");
        }
        Path storageDirectory = resolveStorageDirectory(relativePath);
        Path destinationFile = resolveUploadDestination(storageDirectory, getSafeFileExtension(file.getOriginalFilename()));
        try {
            file.transferTo(destinationFile);
        } catch (IOException ex) {
            throw new GXBusinessException("Failed to save file", ex);
        }
        fileStoragePath = storageDirectory;
        return destinationFile.getFileName().toString();
    }

    @Override
    public String getStoragePath() {
        return fileStoragePath != null ? fileStoragePath.toAbsolutePath().toString() : null;
    }

    @Override
    public boolean deleteFile(String filename) {
        if (CharSequenceUtil.isBlank(filename)) {
            throw new GXBusinessException("File name must not be blank");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\") || filename.contains(":")) {
            throw new GXBusinessException("File name contains unsafe path characters: " + filename);
        }

        Path storageDirectory = fileStoragePath != null ? fileStoragePath : resolveStorageDirectory(null);
        Path destinationFile = storageDirectory.resolve(filename).normalize().toAbsolutePath();
        if (!destinationFile.getParent().equals(storageDirectory.toAbsolutePath())) {
            throw new GXBusinessException("File delete path is outside storage directory");
        }

        try {
            if (Files.notExists(destinationFile)) {
                log.info("File to delete does not exist: {}", destinationFile);
                return true;
            }
            Files.delete(destinationFile);
            log.info("File deleted: {}", destinationFile);
            return true;
        } catch (IOException ex) {
            throw new GXBusinessException(CharSequenceUtil.format("Failed to delete file: {}", destinationFile), ex);
        }
    }

    private Path resolveUploadDestination(Path storageDirectory, String extension) {
        String newFileName = generateFileName(extension);
        Path destinationFile = storageDirectory.resolve(newFileName).normalize().toAbsolutePath();

        if (!destinationFile.getParent().equals(storageDirectory.toAbsolutePath())) {
            throw new GXBusinessException("File storage path is outside storage directory");
        }

        return destinationFile;
    }

    private Path resolveStorageDirectory(String relativePath) {
        Path rootPath = Paths.get(getStorageRoot()).normalize().toAbsolutePath();
        Path storageDirectory = CharSequenceUtil.isBlank(relativePath)
                ? rootPath
                : rootPath.resolve(validateRelativePath(relativePath)).normalize().toAbsolutePath();

        if (!storageDirectory.startsWith(rootPath)) {
            throw new GXBusinessException("Relative path is outside storage root");
        }

        try {
            Files.createDirectories(storageDirectory);
            if (!Files.isDirectory(storageDirectory)) {
                throw new GXBusinessException("Storage path is not a directory: " + storageDirectory);
            }
            return storageDirectory;
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", storageDirectory, e);
            throw new GXBusinessException(CharSequenceUtil.format("Failed to create storage directory: {}", storageDirectory), e);
        }
    }

    private String getStorageRoot() {
        String storageRoot = GXCommonUtils.getEnvironmentValue("upload.depositPath", String.class, DEFAULT_STORAGE_PATH);
        if (CharSequenceUtil.isBlank(storageRoot)) {
            return DEFAULT_STORAGE_PATH;
        }
        return storageRoot;
    }

    private String validateRelativePath(String relativePath) {
        if (relativePath.contains("..") ||
                relativePath.contains(":") ||
                relativePath.startsWith("/") ||
                relativePath.startsWith("\\")) {
            throw new GXBusinessException("Relative path contains unsafe path characters: " + relativePath);
        }
        return relativePath;
    }

    private String getSafeFileExtension(String fileName) {
        if (CharSequenceUtil.isBlank(fileName)) {
            return "bin";
        }
        String extension = com.google.common.io.Files.getFileExtension(fileName);
        if (CharSequenceUtil.isBlank(extension)) {
            return "bin";
        }
        String normalized = extension.toLowerCase();
        return normalized.matches("[a-z0-9][a-z0-9._-]{0,31}") ? normalized : "bin";
    }

    private String generateFileName(String extension) {
        String timestamp = DateUtil.format(new Date(), DatePattern.PURE_DATETIME_MS_PATTERN);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String randomPart = String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        return timestamp + "-" + uuid + "-" + randomPart + "." + extension;
    }
}
