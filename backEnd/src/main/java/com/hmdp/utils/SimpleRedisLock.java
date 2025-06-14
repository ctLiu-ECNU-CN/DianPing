package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        // 获取线程表示
        String threadId =ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean lockResult = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId+"", timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(lockResult);
    }
    @Override
    public void unlock() {
        // 调用 Lua 脚本(满足原子性)
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId =ID_PREFIX + Thread.currentThread().getId();
//
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//
//        // 删除锁
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//
//        }
//    }
}
