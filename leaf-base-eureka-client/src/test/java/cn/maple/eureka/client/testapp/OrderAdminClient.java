package cn.maple.eureka.client.testapp;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "order-service", contextId = "orderAdminClient")
public interface OrderAdminClient {
    @GetMapping("/admin/orders")
    String listAdminOrders();
}
