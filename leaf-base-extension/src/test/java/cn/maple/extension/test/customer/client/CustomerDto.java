package cn.maple.extension.test.customer.client;

import cn.maple.core.framework.dto.GXBaseDto;
import cn.maple.extension.test.customer.domain.CustomerType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CustomerDto extends GXBaseDto {
    private String companyName;
    private String source;  //advertisement, p4p, RFQ, ATM
    private CustomerType customerType; //potential, intentional, important, vip
}
