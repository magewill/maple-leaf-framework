package cn.maple.core.framework.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GXDynamicCallParamAttributeReqDto extends GXBaseReqDto {
    private String javaType;

    private String fieldName;

    private String dataSource;

    private String sourceFieldName;

    private Object fixedAssignedValue;

    private String callBackClassName;

    private String callBackMethodName;
}
