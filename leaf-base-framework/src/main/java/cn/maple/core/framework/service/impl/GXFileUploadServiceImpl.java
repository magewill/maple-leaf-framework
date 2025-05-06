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

/**
 * 文件上传服务实现类
 * <p>
 * 提供文件上传、Base64文件上传和文件删除等功能。
 * 仅在配置 maple.framework.enable.file-upload=true 时启用。
 * 目录创建采用线程安全方式，防止并发问题。
 * </p>
 *
 * @author maple
 */
@Log4j2
@Service
@ConditionalOnExpression("'${maple.framework.enable.file-upload}'.equals('true')")
public class GXFileUploadServiceImpl implements GXFileUploadService {
    /**
     * 文件上传的存储根路径（由配置决定）
     * 仅在 getFileStoragePath 方法中初始化和使用，保证线程安全
     */
    private Path fileStoragePath;

    /**
     * 获取文件扩展名（不含点号），如 jpg、png、pdf
     *
     * @param fileName 文件名
     * @return 扩展名，若文件名为 null 返回 null
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        return com.google.common.io.Files.getFileExtension(fileName);
    }

    /**
     * 上传 MultipartFile 文件到指定相对路径
     * 自动创建存储目录，生成唯一文件名
     *
     * @param relativePath 相对存储路径
     * @param file         上传文件对象
     * @return 生成的文件名（不含路径）
     * @throws GXBusinessException 文件为空、IO异常或路径不安全时抛出
     */
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

    /**
     * 上传 Base64 文件到指定相对路径
     * 自动创建存储目录，生成唯一文件名
     *
     * @param relativePath 相对存储路径
     * @param file         Base64 编码文件
     * @return 生成的文件名（不含路径）
     * @throws GXBusinessException IO异常或路径不安全时抛出
     */
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

    /**
     * 获取上传文件存储根目录绝对路径
     *
     * @return 文件上传根路径
     */
    @Override
    public String getStoragePath() {
        return fileStoragePath != null ? fileStoragePath.toAbsolutePath().toString() : null;
    }

    /**
     * 删除指定文件（根据文件名，不含路径）
     * 文件不存在视为删除成功
     *
     * @param filename 待删除文件名
     * @return 删除成功返回 true，失败抛出异常
     * @throws GXBusinessException 删除失败时抛出
     */
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

    /**
     * 生成唯一文件名并返回完整存储路径，自动创建目录（线程安全）
     * 防止路径遍历攻击，保证目录安全
     *
     * @param relativePath 相对存储路径
     * @param mediaType    文件类型（扩展名）
     * @return 目标文件完整路径
     * @throws GXBusinessException 路径不安全或创建目录失败时抛出
     */
    private Path getFileStoragePath(String relativePath, String mediaType) {
        // 生成唯一文件名（时间戳-UUID-随机数.扩展名）
        String timestamp = DateUtil.format(new Date(), DatePattern.PURE_DATETIME_MS_PATTERN);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
        String randomPart = String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String newFileName = timestamp + "-" + uuid + "-" + randomPart + "." + mediaType;

        // 检查文件名是否包含路径遍历攻击的特征
        if (newFileName.contains("..") || newFileName.contains("/") || newFileName.contains("\\")) {
            throw new GXBusinessException("文件包含无效的路径符号: " + newFileName);
        }

        // 从配置中获取存储根路径，默认为 ./Uploads/files
        String envStoragePath = GXCommonUtils.getEnvironmentValue(
                "upload.depositPath", String.class, "./Uploads/files"
        );

        // 检查相对路径是否包含路径遍历攻击的特征
        if (relativePath != null && (
                relativePath.contains("..") ||
                        relativePath.contains(":") ||
                        relativePath.startsWith("/") ||
                        relativePath.startsWith("\\")
        )) {
            throw new GXBusinessException("相对路径包含无效的路径符号: " + relativePath);
        }

        // 构建并规范化完整存储路径
        String storagePath = CharSequenceUtil.format(
                "{}{}{}", envStoragePath, File.separator,
                relativePath != null ? relativePath : ""
        );
        fileStoragePath = Paths.get(storagePath).normalize();

        // 线程安全地创建目录（Files.createDirectories 是线程安全的）
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

        // 构建目标文件路径
        Path destinationFile = fileStoragePath.resolve(newFileName).toAbsolutePath();

        // 路径安全检查，防止路径遍历
        if (!destinationFile.getParent().equals(fileStoragePath.toAbsolutePath())) {
            throw new GXBusinessException("不能存储文件到当前目录之外，存在路径遍历风险");
        }

        return destinationFile;
    }
}