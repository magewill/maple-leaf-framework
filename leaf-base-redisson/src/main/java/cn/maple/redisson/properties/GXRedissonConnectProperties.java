package cn.maple.redisson.properties;

import lombok.Data;

@Data
public class GXRedissonConnectProperties {
    /**
     * 链接地址
     */
    private String address;

    /**
     * 密码
     */
    private String password;

    /**
     * 数据库
     */
    private Integer database;

    /**
     * 链接用户名
     */
    private String username;

    /**
     * 链接最小闲置大小
     */
    private Integer connectionMinimumIdleSize = 2;

    /**
     * 集群模式 链接最小闲置大小
     */
    private Integer slaveConnectionMinimumIdleSize = 2;

    /**
     * 线程数
     */
    private Integer threads = 2;

    /**
     * Netty线程数
     */
    private Integer nettyThreads = 4;
}
