package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 先查询缓存
        String shopTypeJson = this.stringRedisTemplate.opsForValue().get("cache:shopType:" + id);
        // 存在,返回
        if(StrUtil.isNotBlank(shopTypeJson)){
            ShopType shopType = BeanUtil.copyProperties(shopTypeJson, ShopType.class);
            return Result.ok(shopType);
        }
        // 不存在,查询数据库
        ShopType shopType = getById(id);

        // 如果数据库不存在返回报错信息
        if(shopType== null){
            return Result.fail("数据不存在");
        }
        // 更新缓存,并返回结果
        stringRedisTemplate.opsForValue().set("cache:shopType:" + id, JSONUtil.toJsonStr(shopType));
        return Result.ok(shopType);
    }
}
