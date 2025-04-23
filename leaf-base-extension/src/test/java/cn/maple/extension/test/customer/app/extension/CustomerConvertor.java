package cn.maple.extension.test.customer.app.extension;

import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.test.customer.client.AddCustomerCmd;
import cn.maple.extension.test.customer.client.CustomerDto;
import cn.maple.extension.test.customer.domain.CustomerEntity;
import cn.maple.extension.test.customer.infrastructure.CustomerRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class CustomerConvertor {
    @Resource
    private GXExtensionExecutor extensionExecutor;

    @Resource
    private CustomerRepository customerRepository;

    public CustomerEntity clientToEntity(Object clientObject) {
        AddCustomerCmd addCustomerCmd = (AddCustomerCmd) clientObject;
        CustomerDto customerDto = addCustomerCmd.getCustomerDTO();
        CustomerEntity customerEntity = new CustomerEntity(extensionExecutor, customerRepository);
        customerEntity.setCompanyName(customerDto.getCompanyName());
        customerEntity.setCustomerType(customerDto.getCustomerType());
        customerEntity.setBizScenario(addCustomerCmd.getBizScenario());
        return customerEntity;
    }
}
