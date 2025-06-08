package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id){
        // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    private Shop queryWithMutex(long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //是否存在
        if(StrUtil.isBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if(shopJson != null){
            return null;
        }

        // 缓存重建
        // 获取互斥锁
        String lockKey = "lock:shop:" + id;
        boolean islock = tryLock(lockKey);
        try {
            // 是否获取到了锁
            if(!islock){
                // 失败,休眠
                Thread.sleep(100);
                return queryWithMutex(id);
            }
            // 成功,根据 id 查询数据,并写入 redis
            Shop shop = getById(id);
            if(shop == null){
                //空值写入 redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
            // 返回
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放互斥锁
            unlock(lockKey);
        }
    }

    /**
     * 缓存穿透的一种解决方案,空查询保存在缓存中
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        // 从 redis 中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        // 缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 存在,直接读缓存
            Shop bean = JSONUtil.toBean(shopJson, Shop.class);
            return bean;
        }
        if(shopJson != null){
            return null;
        }
        // 不存在,根据 id 查询数据库
        Shop shop = getById(id);
        // 不存在,返回错误提示
        if(shop == null){
            // 将空值写入 redis
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return shop;
        }
        // 存在 写入 redis 并返回
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
        return shop;
    }
    // 上锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 30L, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 逻辑过期解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        // 从 redis 中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        // 缓存是否存在
        if(StrUtil.isBlank(shopJson)){
            // 不存在,直接返回
            return null;
        }
        // 命中.判断过期时间(先把 json 反 序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);

        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
        // 未过期
            return shop;
        }
        // 过期
        // 缓存重建

        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean islock = tryLock(lockKey);
        // 判断是否获取锁成功
        if(!islock){
            return shop;
            // 返回过期的对象
        }
        // 成功.开启独立线程,实现缓存重建
        CACHA_REBUILD_EXECUTOR.submit(() -> {
            try {
                this.saveShop2Redis(id,20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unlock(lockKey);
            }
        });
        shop = getById(id);
        return shop;
    }

    // 线程池
    private static final ExecutorService CACHA_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void saveShop2Redis(Long id, Long expireSeconds){
        // 查询店铺数据
        Shop shop = getById(id);
        // 逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入 Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("<店铺 id 不能为空>");
        }

        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete("cache:shop:" + id);
        return null;
    }

}
