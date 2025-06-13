package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // 阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    //线程池(单线程)
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        try {
            stringRedisTemplate.opsForStream().createGroup("stream.orders", ReadOffset.latest(), "g1");
//            log.info("创建消费者组 g1 成功");
        } catch (RedisSystemException e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.error("消费者组 g1 已存在，无需重复创建");
            } else {
                throw e;
            }
        }
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    IVoucherOrderService proxy;

    private String queueName = "stream.orders";
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run(){
            while(true){
//                获取队列中的订单信息
                try {
                    // 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
//                    判断消息是否获取成功
                    if(list == null || list.isEmpty()){
//                    失败->下一次循环
                        continue;
                    }
//                    解析订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    成功->下单
                    System.out.println(voucherOrder);
                    handleVoucherOrder(voucherOrder);
//                    ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单出错",e);
//                    尝试在 pendinglist 处理发生异常的消息
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true) {
//                获取队列中的订单信息
                try {
                    // 获取Pending-List队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
//                    判断是否获取Pending-list消息成功
                    if (list == null || list.isEmpty()) {
//                    失败->说明没有异常消息
//                        结束
                        break;
                    }
//                    解析订单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    成功->下单
                    handleVoucherOrder(voucherOrder);
//                    ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单出错", e);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }


//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run(){
//            while(true){
////                获取队列中的订单信息
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单出错");
//                }
//            }
//        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
//            创建锁对象
            Long userId = voucherOrder.getUserId();
            RLock lock = redissonClient.getLock("order" + userId);
        //获取锁
//        boolean isLock = simpleRedisLock.tryLock(1200);
        boolean isLock = lock.tryLock();
        //是否获取锁
        if(!isLock){
            // 获取锁失败,返回错误或重试
            log.error("获取锁失败");
        }
        try {
            //获取代理对象
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
        }
    }
    //Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
//        获取用户
        UserDTO user = UserHolder.getUser();
        long orderId = redisIdWorker.nextId("order");

        // 1.执行 lua 脚本
        Long executeResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                //fix bug 读取出来的 userId 为空(原来没有 getId())
                user.getId().toString(),
                String.valueOf(orderId)
        );

//        判断结果不为 0--没有购买资格
        int r = executeResult.intValue();
        if(r == -1){
            return Result.fail("优惠券不存在");

        }
        if(r != 0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
//判断结果是 0
        //获取代理对象
        proxy  = (IVoucherOrderService) AopContext.currentProxy();
//       下单信息保存到阻塞队列
//        返回订单 id
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        UserDTO user = UserHolder.getUser();
//        // 1.执行 lua 脚本
//        Long executeResult = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                user.toString());
////        判断结果不为 0--没有购买资格
//        int r = executeResult.intValue();
//        if(r != 0){
//            return Result.fail(r==1?"库存不足":"不能重复下单");
//        }
////判断结果是 0
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setVoucherId(voucherId);
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(user.getId());
//
//        //获取代理对象
//        proxy  = (IVoucherOrderService) AopContext.currentProxy();
//
////       下单信息保存到阻塞队列
//
//        orderTasks.add(voucherOrder);
//
////        返回订单 id
//        return Result.ok(orderId);
//    }

    /**
     * 秒杀活动订单生成实现
     * @param
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
    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        查询订单
//        判断是否存在
        Long userId = voucherOrder.getUserId();

        int count = query().eq("user_id",userId).eq("voucher_id", voucherOrder.getVoucherId()).count();

        if(count > 0){
            log.error("不允许重复下单");
            return;
        }
//        扣减库存
        boolean res = seckillVoucherService.update()
                .setSql("stock = stock -1 ")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0) //乐观锁
                .update();
        if(!res){
            //失败
            log.error("秒杀失败");
            return;
        }

        save(voucherOrder);
        return;
    }

}
