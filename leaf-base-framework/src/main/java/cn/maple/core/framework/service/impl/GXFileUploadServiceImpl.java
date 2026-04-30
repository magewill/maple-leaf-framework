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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Log4j2
@Service
@ConditionalOnExpression("'${maple.framework.enable.file-upload}'.equals('true')")
public class GXFileUploadServiceImpl implements GXFileUploadService {
    private Path fileStoragePath;

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        return com.google.common.io.Files.getFileExtension(fileName);
    }

    @Override
    public String upload(String relativePath, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GXBusinessException("文件不能为空");
        }
        Path fileStorageFileName = getFileStoragePath(relativePath, getFileExtension(file.getOriginalFilename()));
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, fileStorageFileName, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new GXBusinessException("不能保存文件，请重试！", ex);
        }
        return fileStorageFileName.getFileName().toString();
    }

    @Override
    public String upload(String relativePath, GXBase64DecodedMultipartFile file) {
        if (file == null) {
            throw new GXBusinessException("文件不能为空");
        }
        String type = getFileExtension(file.getOriginalFilename());
        Path fileStorageFilename = getFileStoragePath(relativePath, type);
        try {
            file.transferTo(fileStorageFilename);
        } catch (IOException ex) {
            throw new GXBusinessException("不能保存文件，请重试！", ex);
        }
        return fileStorageFilename.getFileName().toString();
    }

    @Override
    public String getStoragePath() {
        return fileStoragePath != null ? fileStoragePath.toAbsolutePath().toString() : null;
    }

    @Override
    public boolean deleteFile(String filename) {
        if (filename == null) {
            throw new GXBusinessException("文件名不能为空");
        }
        Path destinationFile = fileStoragePath.resolve(Paths.get(filename)).toAbsolutePath();
        try {
            if (Files.notExists(destinationFile)) {
                log.info("待删除文件{}不存在", destinationFile);
                return true;
            }
            log.info("正在删除文件{}", destinationFile);
            Files.delete(destinationFile);
            log.info("删除文件{}成功", destinationFile);
            return true;
        } catch (Exception ex) {
            throw new GXBusinessException(CharSequenceUtil.format("删除文件{}失败", destinationFile), ex);
        }
    }

    private Path getFileStoragePath(String relativePath, String mediaType) {
        String timestamp = DateUtil.format(new Date(), DatePattern.PURE_DATETIME_MS_PATTERN);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
        String randomPart = String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String newFileName = timestamp + "-" + uuid + "-" + randomPart + "." + mediaType;

        if (newFileName.contains("..") || newFileName.contains("/") || newFileName.contains("\\")) {
            throw new GXBusinessException("文件包含无效的路径符号: " + newFileName);
        }

        String envStoragePath = GXCommonUtils.getEnvironmentValue(
                "upload.depositPath", String.class, "./Uploads/files"
        );

        if (relativePath != null && (
                relativePath.contains("..") ||
                        relativePath.contains(":") ||
                        relativePath.startsWith("/") ||
                        relativePath.startsWith("\\")
        )) {
            throw new GXBusinessException("相对路径包含无效的路径符号: " + relativePath);
        }

        String storagePath = CharSequenceUtil.format(
                "{}{}{}", envStoragePath, File.separator,
                relativePath != null ? relativePath : ""
        );
        fileStoragePath = Paths.get(storagePath).normalize();

        try {
            Files.createDirectories(fileStoragePath);
            log.debug("成功创建或确认目录存在: {}", fileStoragePath);
        } catch (FileAlreadyExistsException e) {
            log.info("目录已存在: {}", fileStoragePath);
        } catch (IOException e) {
            log.error("创建目录失败: {}", fileStoragePath, e);
            throw new GXBusinessException(
                    CharSequenceUtil.format("创建目录 {} 失败", fileStoragePath), e
            );
        }

        Path destinationFile = fileStoragePath.resolve(newFileName).toAbsolutePath();

        if (!destinationFile.getParent().equals(fileStoragePath.toAbsolutePath())) {
            throw new GXBusinessException("不能存储文件到当前目录之外，存在路径遍历风险");
        }

        return destinationFile;
    }
}