package cn.maple.extension.test.customer.client;

import cn.maple.core.framework.dto.GXBaseDto;
import cn.maple.extension.GXBizScenario;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AddCustomerCmd extends GXBaseDto {
    private CustomerDto customerDTO;

    private String biz;

    private GXBizScenario bizScenario;
}
