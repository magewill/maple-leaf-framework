package cn.maple.core.framework.dto.inner;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class GXValidateExistsDto extends GXBaseDto {
    private String fieldName;

    private String tableName;

    @SuppressWarnings("all")
    private Object value;

    private String spEL;

    private Dict condition;

    private Class<?>[] groups;
}
