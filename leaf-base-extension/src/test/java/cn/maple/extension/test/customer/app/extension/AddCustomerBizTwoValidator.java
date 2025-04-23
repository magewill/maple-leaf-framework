package cn.maple.extension.test.customer.app.extension;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.extension.GXExtension;
import cn.maple.extension.test.customer.app.extension.point.AddCustomerValidatorExtPoint;
import cn.maple.extension.test.customer.client.AddCustomerCmd;
import cn.maple.extension.test.customer.client.GXConstants;
import org.springframework.stereotype.Component;

@GXExtension(bizId = GXConstants.BIZ_2)
@Component
public class AddCustomerBizTwoValidator implements AddCustomerValidatorExtPoint {
    public void validate(AddCustomerCmd addCustomerCmd) {
        if (addCustomerCmd.getCustomerDTO().getCustomerType() == null)
            throw new GXBusinessException("CustomerType could not be null");
    }
}
