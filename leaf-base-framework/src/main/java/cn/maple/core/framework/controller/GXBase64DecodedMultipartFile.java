package cn.maple.core.framework.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Objects;

@SuppressWarnings("unused")
public class GXBase64DecodedMultipartFile implements MultipartFile {
    private byte[] imageBytes;

    @Getter
    private String base64;

    private String contentType;

    public GXBase64DecodedMultipartFile(String file) {
        if (CharSequenceUtil.isBlank(file)) {
            throw new GXBusinessException("Base64字符串不能为空");
        }

        this.base64 = file;
        try {
            if (!file.contains("data:") || !file.contains("base64,")) {
                throw new GXBusinessException("Base64字符串格式不正确，应为data:mimetype;base64,开头");
            }

            int colonIndex = file.indexOf(':');
            int semicolonIndex = file.indexOf(';');
            if (colonIndex < 0 || semicolonIndex < 0 || colonIndex >= semicolonIndex) {
                throw new GXBusinessException("无法解析内容类型，Base64字符串格式不正确");
            }
            this.contentType = file.substring(colonIndex + 1, semicolonIndex);

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

    public void setBase64(String base64) {
        if (CharSequenceUtil.isBlank(base64)) {
            throw new GXBusinessException("Base64字符串不能为空");
        }

        try {
            if (!base64.contains("data:") || !base64.contains("base64,")) {
                throw new GXBusinessException("Base64字符串格式不正确，应为data:mimetype;base64,开头");
            }

            int colonIndex = base64.indexOf(':');
            int semicolonIndex = base64.indexOf(';');
            if (colonIndex < 0 || semicolonIndex < 0 || colonIndex >= semicolonIndex) {
                throw new GXBusinessException("无法解析内容类型，Base64字符串格式不正确");
            }
            this.contentType = base64.substring(colonIndex + 1, semicolonIndex);

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

    @Override
    public String getOriginalFilename() {
        return getName();
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return imageBytes == null || imageBytes.length == 0;
    }

    @Override
    public long getSize() {
        return imageBytes.length;
    }

    @Override
    public byte[] getBytes() throws IOException {
        if (imageBytes == null) {
            return new byte[0];
        }
        return imageBytes;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (imageBytes == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(imageBytes);
    }

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
