package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 创建配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379").setPassword("347934");
        // 创建 RedissonClient 对象
        return Redisson.create(config);
    }
}
