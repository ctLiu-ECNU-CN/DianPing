package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
    /**
     * 秒杀活动订单生成实现
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {


//        1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动尚未开始");
        }
//        3.判断秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("活动已经结束");
        }

//        4.判断库存是否充足
        if ( voucher.getStock()<1) {
            return Result.fail("库存不足");
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
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
//        绑定代金券 id
        voucherOrder.setVoucherId(voucherId);
//        返回订单 id
//        保存到数据库
        save(voucherOrder);
        return Result.ok(voucherId);
    }

}
