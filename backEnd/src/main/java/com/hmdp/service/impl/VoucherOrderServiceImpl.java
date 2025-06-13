package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        // 1.执行 lua 脚本
        Long executeResult = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                user.toString());
//判断结果是 0
        int r = executeResult.intValue();
        if(r != 0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
//        判断结果不为 0--没有购买资格


//        如果有购买资格,奖下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
//TODO 放入阻塞队列
//        返回订单 id
        return Result.ok(orderId);
    }

    /**
     * 秒杀活动订单生成实现
     * @param voucherId
     * @return
     */
//    @Override
//    @Transactional
//    public Result seckillVoucher(Long voucherId) {
//
//
////        1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
////        判断秒杀是否开始
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("活动尚未开始");
//        }
//
//
////        3.判断秒杀是否结束
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("活动已经结束");
//        }
//
////        4.判断库存是否充足
//        if ( voucher.getStock()<1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //intern是从常量池里找相同的对象,
////        synchronized (userId.toString().intern()) {
////            // 获取代理对象 (防止事务还没提交,就释放了锁)
////            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("order" + userId);
//        //获取锁
////        boolean isLock = simpleRedisLock.tryLock(1200);
//        boolean isLock = lock.tryLock();
//        //是否获取锁
//        if(!isLock){
//            // 获取锁失败,返回错误或重试
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
//            return voucherOrderService.seckillVoucher(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //TODO 一人限购一单
//        查询订单
//        判断是否存在
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id",userId).eq("voucher_id", voucherId).count();

        if(count > 0){
            return Result.fail("用户已经购买过一次");
        }
//        扣减库存
        boolean res = seckillVoucherService.update()
                .setSql("stock = stock -1 ")
                .eq("voucher_id", voucherId)
                .gt("stock",0) //乐观锁
                .update();
        if(!res){
            //失败
            return Result.fail("秒杀失败");
        }
//        创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
//        生成订单 id
        long orderID = redisIdWorker.nextId("order");
        voucherOrder.setId(orderID);
//        获取用户 id
//        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
//        绑定代金券 id
        voucherOrder.setVoucherId(voucherId);
//        返回订单 id
//        保存到数据库
        save(voucherOrder);
        return Result.ok(voucherId);

    }

}
