
-- 比较线程标识(uuid+currentthreadid) 与锁中的是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 释放锁
    return redis.call('del', KEYS[1])
end
-- 不一致,返回 0
return 0