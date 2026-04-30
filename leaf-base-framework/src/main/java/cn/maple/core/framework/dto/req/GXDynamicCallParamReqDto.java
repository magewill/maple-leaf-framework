package cn.maple.core.framework.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class GXDynamicCallParamReqDto extends GXBaseReqDto {
    private String javaType;

    private List<GXDynamicCallParamAttributeReqDto> attributes = new ArrayList<>(0);
}