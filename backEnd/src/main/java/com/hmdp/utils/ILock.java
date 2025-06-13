package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁,非阻塞式(尝试获取一次就放弃)
     * @param timeoutSeconds 锁持有的时间,过期后自动释放锁
     * @return true成功,false 代表失败
     */
    boolean tryLock(long timeoutSeconds);

    /**
     * 主动释放锁
     */
    void unlock();
}
