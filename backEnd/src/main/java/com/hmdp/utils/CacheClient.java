package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key,Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    public void setWithLogicalExpire(String key,Object value, Long time, TimeUnit timeUnit) {
        //整合到 RedisData 里,添加逻辑过期字段
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        // 写入 redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 从 redis 中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 存在,直接读缓存
            return JSONUtil.toBean(shopJson, type);

        }
        if(shopJson != null){
            return null;
        }
        // 不存在,根据 id 查询数据库
        R r = dbFallback.apply(id);
        // 不存在,返回错误提示
        if(r == null){
            // 将空值写入 redis
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 存在 写入 redis 并返回
        this.set(key,r,time,unit);
        return r;
    }

}
