package cn.maple.core.framework.dto.res;

import cn.maple.core.framework.dto.GXBaseDto;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GXBaseResDto extends GXBaseDto {
    public <R> R getBean(Class<R> clazz) {
        return GXSpringContextUtils.getBean(clazz);
    }

    public <R> R getBean(String beanName, Class<R> requiredType) {
        return GXSpringContextUtils.getBean(beanName, requiredType);
    }
}
