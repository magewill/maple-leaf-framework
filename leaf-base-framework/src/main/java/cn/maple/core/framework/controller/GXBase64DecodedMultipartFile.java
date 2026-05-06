package cn.maple.core.framework.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class GXBase64DecodedMultipartFile implements MultipartFile {
    private static final Pattern BASE64_PAYLOAD_PATTERN = Pattern.compile("^[A-Za-z0-9+/]*={0,2}$");

    private byte[] imageBytes;

    @Getter
    private String base64;

    private String contentType;

    public GXBase64DecodedMultipartFile(String file) {
        setBase64(file);
    }

    public void setBase64(String base64) {
        DecodedFile decodedFile = decode(base64);
        this.base64 = base64;
        this.contentType = decodedFile.contentType();
        this.imageBytes = decodedFile.bytes();
    }

    @Override
    @NullMarked
    public String getName() {
        return "base64." + getSafeExtension();
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
        return imageBytes == null ? 0L : imageBytes.length;
    }

    @Override
    @NullMarked
    public byte[] getBytes() throws IOException {
        return imageBytes == null ? new byte[0] : Arrays.copyOf(imageBytes, imageBytes.length);
    }

    @Override
    @NullMarked
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(imageBytes == null ? new byte[0] : imageBytes);
    }

    @Override
    @NullMarked
    public void transferTo(@NotNull File dest) throws IOException {
        Objects.requireNonNull(dest, "Destination file must not be null");
        transferTo(dest.toPath());
    }

    @Override
    @NullMarked
    public void transferTo(@NotNull Path dest) throws IOException {
        Objects.requireNonNull(dest, "Destination path must not be null");
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(dest) && !Files.isWritable(dest)) {
            throw new IOException("Destination file is not writable: " + dest.toAbsolutePath());
        }

        try {
            Files.write(dest, imageBytes == null ? new byte[0] : imageBytes);
        } catch (IOException e) {
            throw new IOException("Failed to write destination file: " + e.getMessage(), e);
        }
    }

    private DecodedFile decode(String source) {
        if (CharSequenceUtil.isBlank(source)) {
            throw new GXBusinessException("Base64 data must not be blank");
        }
        if (!source.startsWith("data:")) {
            throw new GXBusinessException("Base64 data must start with data:");
        }

        int commaIndex = source.indexOf(',');
        if (commaIndex < 0 || commaIndex >= source.length() - 1) {
            throw new GXBusinessException("Base64 payload is missing");
        }

        String metadata = source.substring(5, commaIndex);
        int base64MarkerIndex = metadata.toLowerCase(Locale.ROOT).lastIndexOf(";base64");
        if (base64MarkerIndex <= 0) {
            throw new GXBusinessException("Base64 data must use data:mimetype;base64 format");
        }

        String parsedContentType = metadata.substring(0, base64MarkerIndex).trim();
        if (CharSequenceUtil.isBlank(parsedContentType) || !parsedContentType.contains("/")) {
            throw new GXBusinessException("Base64 content type is invalid");
        }

        String payload = source.substring(commaIndex + 1).trim();
        if (payload.length() % 4 != 0 || !BASE64_PAYLOAD_PATTERN.matcher(payload).matches()) {
            throw new GXBusinessException("Base64 payload is invalid");
        }

        try {
            byte[] bytes = Base64.decode(payload);
            if (bytes.length == 0) {
                throw new GXBusinessException("Decoded Base64 payload must not be empty");
            }
            return new DecodedFile(parsedContentType, bytes);
        } catch (IllegalArgumentException e) {
            throw new GXBusinessException("Base64 decode failed: " + e.getMessage(), e);
        }
    }

    private String getSafeExtension() {
        if (CharSequenceUtil.isBlank(contentType)) {
            return "bin";
        }
        int slashIndex = contentType.indexOf('/');
        if (slashIndex < 0 || slashIndex >= contentType.length() - 1) {
            return "bin";
        }
        String extension = contentType.substring(slashIndex + 1).toLowerCase(Locale.ROOT);
        int suffixIndex = extension.indexOf('+');
        if (suffixIndex > 0) {
            extension = extension.substring(0, suffixIndex);
        }
        return extension.matches("[a-z0-9][a-z0-9._-]*") ? extension : "bin";
    }

    private record DecodedFile(String contentType, byte[] bytes) {
    }
}
