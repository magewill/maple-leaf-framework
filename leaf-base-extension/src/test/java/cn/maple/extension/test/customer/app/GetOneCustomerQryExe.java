package cn.maple.extension.test.customer.app;

import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.extension.test.customer.client.CustomerDto;
import cn.maple.extension.test.customer.client.GetOneCustomerQry;
import org.springframework.stereotype.Component;

@Component
public class GetOneCustomerQryExe {
    public GXResultUtils<CustomerDto> execute(GetOneCustomerQry getOneCustomerQry) {
        return null;
    }
}
