package cn.maple.extension.test.customer.domain.rule;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.extension.GXExtension;
import cn.maple.extension.test.customer.client.GXConstants;
import cn.maple.extension.test.customer.domain.CustomerEntity;
import cn.maple.extension.test.customer.domain.SourceType;
import org.springframework.stereotype.Component;

@GXExtension(bizId = GXConstants.BIZ_1)
@Component
public class CustomerBizOneRuleExt implements CustomerRuleExtPoint {
    @Override
    public boolean addCustomerCheck(CustomerEntity customerEntity) {
        if (SourceType.AD == customerEntity.getSourceType()) {
            throw new GXBusinessException("Sorry, Customer from advertisement can not be added in this period");
        }
        return true;
    }
}
