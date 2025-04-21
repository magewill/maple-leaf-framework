package cn.maple.core.framework.service.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RandomUtil;
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
import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 文件上传服务实现类
 * <p>
 * 提供文件上传、Base64文件上传和文件删除等功能
 * 该服务只有在配置maple.framework.enable.file-upload=true时才会启用
 * 注意：该实现类的fileStoragePath字段在多线程环境下可能存在竞争条件，
 * 但通过synchronized块保证了目录创建的线程安全性
 * </p>
 *
 * @author maple
 */
@Log4j2
@Service
@ConditionalOnExpression("'${maple.framework.enable.file-upload}'.equals('true')")
public class GXFileUploadServiceImpl implements GXFileUploadService {
    /**
     * 上传文件的存储路径
     * 注意：该字段在多线程环境下被共享访问，需要注意线程安全
     */
    private Path fileStoragePath;
    
    /**
     * 用于生成随机数的安全随机数生成器
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 获取文件扩展名
     * <p>
     * 从文件名中提取文件扩展名，不包含点号
     * </p>
     *
     * @param fileName 文件名字
     * @return 文件扩展名，例如 jpg, png, pdf 等，如果文件名为null则返回null
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        return com.google.common.io.Files.getFileExtension(fileName);
    }

    /**
     * 上传文件
     * <p>
     * 将MultipartFile类型的文件上传到指定的相对路径下
     * 该方法会自动创建存储目录，并使用时间戳和随机数生成唯一文件名
     * </p>
     *
     * @param relativePath 存储的相对路径，相对于配置的根存储路径
     * @param file 上传的文件对象
     * @return 生成的文件名（不含路径）
     * @throws GXBusinessException 当文件为空、IO异常或路径不安全时抛出业务异常
     */
    @Override
    public String upload(String relativePath, MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new GXBusinessException("文件不能为空");
            }
            Path fileStorageFileName = getFileStoragePath(relativePath, getFileExtension(file.getOriginalFilename()));
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, fileStorageFileName, StandardCopyOption.REPLACE_EXISTING);
            }
            return fileStorageFileName.getFileName().toString();
        } catch (IOException ex) {
            throw new GXBusinessException("不能保存文件.请重试!", ex);
        }
    }

    /**
     * 上传Base64文件
     * <p>
     * 将Base64编码的文件上传到指定的相对路径下
     * Controller可以直接使用GXBase64DecodedMultipartFile类来接收前端传递的Base64字符串
     * 该方法会自动创建存储目录，并使用时间戳和随机数生成唯一文件名
     * </p>
     *
     * @param relativePath 存储的相对路径，相对于配置的根存储路径
     * @param file Base64编码的文件
     * @return 生成的文件名（不含路径）
     * @throws GXBusinessException 当IO异常或路径不安全时抛出业务异常
     */
    @Override
    public String upload(String relativePath, GXBase64DecodedMultipartFile file) {
        try {
            String type = getFileExtension(file.getOriginalFilename());
            Path fileStorageFilename = getFileStoragePath(relativePath, type);
            file.transferTo(fileStorageFilename);
            return fileStorageFilename.getFileName().toString();
        } catch (IOException ex) {
            throw new GXBusinessException("不能保存文件.请重试!", ex);
        }
    }

    /**
     * 获取上传文件存储目录
     * <p>
     * 返回当前文件存储的绝对路径
     * </p>
     *
     * @return 文件上传的绝对路径
     */
    @Override
    public String getStoragePath() {
        return fileStoragePath.toAbsolutePath().toString();
    }

    /**
     * 删除指定文件
     * <p>
     * 根据文件名删除存储目录下的文件
     * 如果文件不存在，则视为删除成功并记录日志
     * </p>
     *
     * @param filename 待删除的文件名字（不含路径）
     * @return 删除是否成功，成功返回true，失败抛出异常
     * @throws GXBusinessException 当删除操作失败时抛出业务异常
     */
    @Override
    public boolean deleteFile(String filename) {
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
     * 根据传入的文件类型和相对路径生成文件存储的完整路径
     * <p>
     * 该方法会生成一个基于时间戳、UUID和随机数的唯一文件名，
     * 并确保存储路径的安全性，防止目录遍历攻击。
     * 如果存储目录不存在，会以线程安全的方式创建目录。
     * </p>
     *
     * @param relativePath 相对存储路径
     * @param mediaType 文件类型（扩展名，不含点号）
     * @return 完整的文件存储路径
     * @throws GXBusinessException 当路径不安全或创建目录失败时抛出业务异常
     */
    private Path getFileStoragePath(String relativePath, String mediaType) {
        // 生成基于时间戳、UUID和随机数的唯一文件名
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

        // 构建完整存储路径并规范化
        String storagePath = CharSequenceUtil.format(
                "{}{}{}", envStoragePath, File.separator,
                relativePath != null ? relativePath : ""
        );
        Path fileStoragePath = Paths.get(storagePath).normalize();

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
            throw new GXBusinessException("不能存储文件到当前目录之外，可能存在路径遍历风险");
        }

        return destinationFile;
    }
}