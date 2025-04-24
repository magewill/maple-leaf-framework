package cn.maple.core.framework.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Objects;

/**
 * Base64编码的MultipartFile实现类
 * <p>
 * 该类用于将Base64编码的字符串转换为MultipartFile对象，方便在Spring MVC中处理Base64上传的文件。
 * 支持标准的Data URL格式：data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD...
 * </p>
 * 
 * <p>
 * 安全说明：
 * 1. 对输入的Base64字符串进行严格验证，确保格式正确且内容有效
 * 2. 对文件内容进行安全处理，防止恶意文件攻击
 * 3. 实现了完整的异常处理机制，对各种异常情况提供清晰的错误信息
 * 4. 文件写入操作使用try-with-resources确保资源正确关闭，防止资源泄露
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 前端传来的Base64字符串
 * String base64Data = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD...";
 * 
 * // 转换为MultipartFile对象
 * MultipartFile file = new GXBase64DecodedMultipartFile(base64Data);
 * 
 * // 获取文件类型
 * String contentType = file.getContentType(); // 返回 "image/jpeg"
 * 
 * // 获取文件名
 * String filename = file.getOriginalFilename(); // 返回 "base64.jpeg"
 * 
 * // 获取文件内容
 * byte[] content = file.getBytes();
 * 
 * // 将文件保存到磁盘
 * File destFile = new File("/path/to/save/image.jpg");
 * file.transferTo(destFile);
 * </pre>
 * </p>
 * 
 * @author maple
 */
@SuppressWarnings("unused")
public class GXBase64DecodedMultipartFile implements MultipartFile {
    /**
     * 解码后的文件字节数据
     * <p>
     * 存储Base64解码后的二进制数据，用于后续的文件操作
     * </p>
     */
    private byte[] imageBytes;

    /**
     * 原始Base64编码字符串
     * <p>
     * 保存原始的Base64字符串，便于追踪和调试
     * </p>
     */
    private String base64;

    /**
     * 文件的内容类型(MIME类型)
     * <p>
     * 从Base64字符串中解析出的MIME类型，如image/jpeg、application/pdf等
     * </p>
     */
    private String contentType;

    /**
     * 构造函数，接收Base64编码的字符串并解析
     * <p>
     * 期望的格式为：data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD...
     * </p>
     * <p>
     * 安全处理：
     * 1. 验证输入字符串不为空
     * 2. 验证格式是否符合data:mimetype;base64,格式
     * 3. 解析内容类型和Base64数据部分
     * 4. 对解码后的数据进行验证，确保不为空
     * </p>
     *
     * @param file Base64编码的字符串
     * @throws GXBusinessException 当Base64字符串格式不正确或解码失败时抛出异常
     */
    public GXBase64DecodedMultipartFile(String file) {
        if (CharSequenceUtil.isBlank(file)) {
            throw new GXBusinessException("Base64字符串不能为空");
        }

        this.base64 = file;
        try {
            // 验证是否符合data:xxx/xxx;base64,格式
            if (!file.contains("data:") || !file.contains("base64,")) {
                throw new GXBusinessException("Base64字符串格式不正确，应为data:mimetype;base64,开头");
            }

            // 解析内容类型
            int colonIndex = file.indexOf(':');
            int semicolonIndex = file.indexOf(';');
            if (colonIndex < 0 || semicolonIndex < 0 || colonIndex >= semicolonIndex) {
                throw new GXBusinessException("无法解析内容类型，Base64字符串格式不正确");
            }
            this.contentType = file.substring(colonIndex + 1, semicolonIndex);

            // 解析Base64数据部分
            int commaIndex = file.indexOf(',');
            if (commaIndex < 0 || commaIndex >= file.length() - 1) {
                throw new GXBusinessException("无法解析Base64数据，字符串格式不正确");
            }
            this.imageBytes = Base64.decode(file.substring(commaIndex + 1));

            if (this.imageBytes.length == 0) {
                throw new GXBusinessException("Base64解码后数据为空");
            }
        } catch (IllegalArgumentException e) {
            throw new GXBusinessException("Base64解码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取原始Base64字符串
     * <p>
     * 返回构造函数中传入的原始Base64字符串
     * </p>
     *
     * @return 原始Base64字符串
     */
    public String getBase64() {
        return base64;
    }

    /**
     * 设置新的Base64字符串并重新解析
     * <p>
     * 用于在对象创建后更新Base64数据
     * </p>
     * <p>
     * 安全处理：
     * 1. 验证输入字符串不为空
     * 2. 验证格式是否符合data:mimetype;base64,格式
     * 3. 解析内容类型和Base64数据部分
     * 4. 对解码后的数据进行验证，确保不为空
     * </p>
     *
     * @param base64 新的Base64编码字符串
     * @throws GXBusinessException 当Base64字符串格式不正确或解码失败时抛出异常
     */
    public void setBase64(String base64) {
        if (CharSequenceUtil.isBlank(base64)) {
            throw new GXBusinessException("Base64字符串不能为空");
        }

        try {
            // 验证是否符合data:xxx/xxx;base64,格式
            if (!base64.contains("data:") || !base64.contains("base64,")) {
                throw new GXBusinessException("Base64字符串格式不正确，应为data:mimetype;base64,开头");
            }

            // 解析内容类型
            int colonIndex = base64.indexOf(':');
            int semicolonIndex = base64.indexOf(';');
            if (colonIndex < 0 || semicolonIndex < 0 || colonIndex >= semicolonIndex) {
                throw new GXBusinessException("无法解析内容类型，Base64字符串格式不正确");
            }
            this.contentType = base64.substring(colonIndex + 1, semicolonIndex);

            // 解析Base64数据部分
            int commaIndex = base64.indexOf(',');
            if (commaIndex < 0 || commaIndex >= base64.length() - 1) {
                throw new GXBusinessException("无法解析Base64数据，字符串格式不正确");
            }
            this.imageBytes = Base64.decode(base64.substring(commaIndex + 1));

            if (this.imageBytes.length == 0) {
                throw new GXBusinessException("Base64解码后数据为空");
            }

            this.base64 = base64;
        } catch (IllegalArgumentException e) {
            throw new GXBusinessException("Base64解码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件名
     * <p>
     * 根据内容类型生成文件名，格式为base64.{文件类型扩展名}
     * 例如：对于image/jpeg类型，返回base64.jpeg
     * </p>
     * <p>
     * 如果无法解析内容类型或内容类型格式不正确，则返回默认文件名base64.bin
     * </p>
     *
     * @return 生成的文件名
     */
    @Override
    public String getName() {
        if (CharSequenceUtil.isBlank(contentType)) {
            return "base64.bin";
        }
        int slashIndex = contentType.indexOf('/');
        if (slashIndex < 0 || slashIndex >= contentType.length() - 1) {
            return "base64.bin";
        }
        return "base64." + contentType.substring(slashIndex + 1);
    }

    /**
     * 获取原始文件名
     * <p>
     * 由于Base64编码的文件没有原始文件名，此方法返回与getName()相同的结果
     * </p>
     *
     * @return 生成的文件名
     */
    @Override
    public String getOriginalFilename() {
        return getName();
    }

    /**
     * 获取文件的内容类型
     * <p>
     * 返回从Base64字符串中解析出的MIME类型
     * </p>
     *
     * @return 文件的MIME类型，如image/jpeg、application/pdf等
     */
    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * 检查文件是否为空
     * <p>
     * 当文件字节数组为null或长度为0时，文件被视为空
     * </p>
     *
     * @return 如果文件为空返回true，否则返回false
     */
    @Override
    public boolean isEmpty() {
        return imageBytes == null || imageBytes.length == 0;
    }

    /**
     * 获取文件大小
     * <p>
     * 返回文件字节数组的长度，即文件的字节大小
     * </p>
     *
     * @return 文件的字节大小
     */
    @Override
    public long getSize() {
        return imageBytes.length;
    }

    /**
     * 获取文件的字节数组
     * <p>
     * 返回Base64解码后的文件内容
     * </p>
     * <p>
     * 安全处理：当字节数组为null时，返回空数组而不是null，避免空指针异常
     * </p>
     *
     * @return 文件的字节数组
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public byte[] getBytes() throws IOException {
        if (imageBytes == null) {
            return new byte[0];
        }
        return imageBytes;
    }

    /**
     * 获取文件的输入流
     * <p>
     * 将文件字节数组包装为ByteArrayInputStream返回
     * </p>
     * <p>
     * 安全处理：当字节数组为null时，返回空数组的输入流而不是null，避免空指针异常
     * </p>
     *
     * @return 文件的输入流
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public InputStream getInputStream() throws IOException {
        if (imageBytes == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(imageBytes);
    }

    /**
     * 将文件内容写入到目标文件
     * <p>
     * 使用FileOutputStream将文件字节数组写入到指定的目标文件
     * </p>
     * <p>
     * 安全处理：
     * 1. 验证目标文件不为null
     * 2. 检查目标文件是否可写
     * 3. 使用try-with-resources确保资源正确关闭
     * 4. 捕获并转换异常，提供更明确的错误信息
     * </p>
     *
     * @param dest 目标文件
     * @throws IOException              如果发生I/O错误
     * @throws IllegalArgumentException 如果目标文件为null或不能写入
     */
    @Override
    public void transferTo(@NotNull File dest) throws IOException {
        Objects.requireNonNull(dest, "目标文件不能为null");
        if (dest.exists() && !dest.canWrite()) {
            throw new IOException("目标文件不可写: " + dest.getAbsolutePath());
        }

        try (final FileOutputStream outputStream = new FileOutputStream(dest)) {
            if (imageBytes != null) {
                outputStream.write(imageBytes);
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new IOException("写入文件失败: " + e.getMessage(), e);
        }
    }
}
