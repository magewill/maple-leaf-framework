package cn.maple.eureka.client.testapp;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "order-service")
public interface OrderClient {
    @GetMapping("/orders")
    String listOrders();
}
