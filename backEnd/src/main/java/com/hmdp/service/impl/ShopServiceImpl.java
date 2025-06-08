package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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

    @Override
    public Result queryById(Long id){
        // 从 redis 中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        // 缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
        // 存在,直接读缓存


                Shop bean = JSONUtil.toBean(shopJson, Shop.class);
                return Result.ok(bean);


        }
        if(shopJson != null){
            return Result.fail("店铺不存在");
        }
        // 不存在,根据 id 查询数据库
        Shop shop = getById(id);
        // 不存在,返回错误提示
        if(shop == null){
            // 将空值写入 redis
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        // 存在 写入 redis 并返回
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
        return Result.ok(shop);
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
