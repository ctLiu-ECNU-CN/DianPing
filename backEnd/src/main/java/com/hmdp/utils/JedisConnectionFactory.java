package com.hmdp.utils;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {
    private static final JedisPool jedispool;
    static {
        // 配置连接池
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(8);
        config.setMaxIdle(8);
        config.setMinIdle(0);
        // 等待时长
        config.setMaxWaitMillis(1000);
        // 创建连接池对象
        jedispool = new JedisPool(config,
                "127.0.0.1", 6379,1000,"347934");
    }
}
