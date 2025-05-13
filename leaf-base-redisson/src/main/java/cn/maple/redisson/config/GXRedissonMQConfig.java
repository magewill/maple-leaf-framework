package cn.maple.redisson.config;

import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.redisson.properties.GXRedissonMQProperties;
import jakarta.annotation.Resource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = {"org.redisson.Redisson"})
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonMQConfig {
    /**
     * Redisson消息队列配置属性
     * 专用于消息队列操作的配置，与标准配置隔离，避免相互影响
     */
    @Resource
    private GXRedissonMQProperties redissonMQConfig;

    /**
     * 创建消息队列专用Redisson配置
     * <p>
     * 处理MQ连接信息，包括地址、密码和用户名的解码
     * 使用线程安全的方式处理配置转换，避免并发问题
     * </p>
     *
     * @return 消息队列专用Redisson配置对象
     */
    @Bean("mqConfig")
    public Config mqConfig() {
        redissonMQConfig.getConfig().forEach((k, v) -> {
            v.setAddress(GXCommonUtils.decodeConnectStr(v.getAddress(), String.class));
            v.setPassword(GXCommonUtils.decodeConnectStr(v.getPassword(), String.class));
            v.setUsername(GXCommonUtils.decodeConnectStr(v.getUsername(), String.class));
        });
        return JSONUtil.toBean(JSONUtil.toJsonStr(redissonMQConfig.getConfig()), Config.class);
    }

    /**
     * 创建消息队列专用Redisson客户端
     * <p>
     * 使用独立的客户端实例处理消息队列操作，避免与标准操作互相影响
     * 同样使用JsonJacksonCodec作为默认编解码器
     * </p>
     *
     * @param mqConfig 消息队列Redisson配置对象
     * @return 消息队列专用RedissonClient实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonMQClient(Config mqConfig) {
        Codec jsonJacksonCodec = new JsonJacksonCodec();
        mqConfig.setCodec(jsonJacksonCodec);
        return Redisson.create(mqConfig);
    }
}
