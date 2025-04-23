package cn.maple.extension.test.customer.app.extension;

import cn.maple.extension.GXExtension;
import cn.maple.extension.test.customer.app.extension.point.CustomerConvertorExtPoint;
import cn.maple.extension.test.customer.client.AddCustomerCmd;
import cn.maple.extension.test.customer.client.GXConstants;
import cn.maple.extension.test.customer.domain.CustomerEntity;
import cn.maple.extension.test.customer.domain.SourceType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@GXExtension(bizId = GXConstants.BIZ_2)
@Component
public class CustomerBizTwoConvertorExt implements CustomerConvertorExtPoint {
    @Resource
    private CustomerConvertor customerConvertor;//Composite basic convertor to do basic conversion

    @Override
    public CustomerEntity clientToEntity(AddCustomerCmd addCustomerCmd) {
        CustomerEntity customerEntity = customerConvertor.clientToEntity(addCustomerCmd);
        //In this business, if customers from RFQ and Advertisement are both regarded as Advertisement
        if (GXConstants.SOURCE_AD.equals(addCustomerCmd.getCustomerDTO().getSource()) || GXConstants.SOURCE_RFQ.equals(addCustomerCmd.getCustomerDTO().getSource())) {
            customerEntity.setSourceType(SourceType.AD);
        }
        return customerEntity;
    }
}
