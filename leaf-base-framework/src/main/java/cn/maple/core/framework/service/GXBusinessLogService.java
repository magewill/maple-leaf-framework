package cn.maple.core.framework.service;

import cn.maple.core.framework.dto.inner.GXBusinessLogDto;

public interface GXBusinessLogService {
    void saveBusinessLog(GXBusinessLogDto businessLogDto);

    String getUserName();
}
