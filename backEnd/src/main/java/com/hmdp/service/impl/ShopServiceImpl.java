package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
            Shop bean = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(bean);
        }
        // 存在,直接读缓存
        Shop shop = getById(id);
        // 不存在,根据 id 查询数据库
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        // 不存在,返回错误提示
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop));
        // 存在 写入 redis 并返回
        return Result.ok(shop);
    }

}
