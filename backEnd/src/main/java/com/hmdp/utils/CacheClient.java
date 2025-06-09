package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

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
            log.info("缓存命中");
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

    public <R,ID> R queryWithLogicalExpire(String preFix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = preFix + id;
        // 从 redis 中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 缓存是否存在
        if(StrUtil.isBlank(shopJson)){
            // 不存在,直接返回
            return null;
        }
        // 命中.判断过期时间(先把 json 反 序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        // TODO fix expioreTime null
        if(expireTime.isAfter(LocalDateTime.now())){
            // 未过期
            return r;
        }
        // 过期
        // 缓存重建

        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean islock = tryLock(lockKey);
        // 判断是否获取锁成功
        if(!islock){
            // 返回过期的对象
            return r;
        }
        // 成功.开启独立线程,实现缓存重建
        CACHA_REBUILD_EXECUTOR.submit(() -> {
            try {
                //查询数据库
                R r1 = dbFallback.apply(id);
                this.setWithLogicalExpire(key,r1,time,unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unlock(lockKey);
            }
        });

        return r;
    }

    private static final ExecutorService CACHA_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    // 上锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 30L, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}
