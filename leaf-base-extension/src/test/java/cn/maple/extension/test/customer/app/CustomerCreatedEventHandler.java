package cn.maple.extension.test.customer.app;

import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.extension.test.customer.client.CustomerCreatedEvent;

public class CustomerCreatedEventHandler {
    public GXResultUtils<String> execute(CustomerCreatedEvent customerCreatedEvent) {
        System.out.println("customerCreatedEvent processed");
        return null;
    }
}
