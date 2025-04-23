package cn.maple.extension.test.customer.app.extension;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.extension.GXExtension;
import cn.maple.extension.test.customer.app.extension.point.AddCustomerValidatorExtPoint;
import cn.maple.extension.test.customer.client.AddCustomerCmd;
import cn.maple.extension.test.customer.client.GXConstants;
import cn.maple.extension.test.customer.domain.CustomerType;
import org.springframework.stereotype.Component;

@GXExtension(bizId = GXConstants.BIZ_1)
@Component
public class AddCustomerBizOneValidator implements AddCustomerValidatorExtPoint {
    public void validate(AddCustomerCmd addCustomerCmd) {
        //For BIZ TWO CustomerTYpe could not be VIP
        if (CustomerType.VIP == addCustomerCmd.getCustomerDTO().getCustomerType())
            throw new GXBusinessException("Customer Type could not be VIP for Biz One");
    }
}
