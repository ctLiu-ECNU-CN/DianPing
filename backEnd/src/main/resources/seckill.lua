-- 秒杀逻辑的 Lua 脚本
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId

-- 判断优惠券是否存在
if (redis.call('exists', stockKey) == 0) then
    return -1  -- 优惠券不存在
end

-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1  -- 库存不足
end

-- 判断用户是否已下单
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2  -- 用户已下单
end

-- 扣减库存
redis.call('incrby', stockKey, -1)

-- 记录用户订单
redis.call('sadd', orderKey, userId)

-- 向消息队列中添加订单信息
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

return 0  -- 秒杀成功
