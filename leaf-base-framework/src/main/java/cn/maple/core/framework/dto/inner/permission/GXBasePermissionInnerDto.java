package cn.maple.core.framework.dto.inner.permission;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class GXBasePermissionInnerDto extends GXBaseDto {
    private String permissionName;

    private String permissionCode;

    private String moduleName;

    private String moduleCode;
}
