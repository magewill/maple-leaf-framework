package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("all")
@Data
public class GXBusinessLogDto extends GXBaseDto {
    private String businessName;

    private String businessDescription;

    private String methodName;

    private String params;

    private String userName;

    private String ip;

    private Long executionTime;

    private Long requestAt;
}
