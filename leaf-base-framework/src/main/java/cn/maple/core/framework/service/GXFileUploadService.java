package cn.maple.core.framework.service;

import cn.maple.core.framework.controller.GXBase64DecodedMultipartFile;
import org.springframework.web.multipart.MultipartFile;

public interface GXFileUploadService {
    String upload(String relativePath, MultipartFile file);

    String upload(String relativePath, GXBase64DecodedMultipartFile file);

    String getStoragePath();

    boolean deleteFile(String filename);
}
