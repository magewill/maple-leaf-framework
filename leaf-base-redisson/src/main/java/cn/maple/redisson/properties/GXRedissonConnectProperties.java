package cn.maple.redisson.properties;

import lombok.Data;

@Data
public class GXRedissonConnectProperties {
    private String address;

    private String password;

    private Integer database;

    private String username;

    private Integer connectionMinimumIdleSize = 2;

    private Integer slaveConnectionMinimumIdleSize = 2;

    private Integer threads = 2;

    private Integer nettyThreads = 4;
}
