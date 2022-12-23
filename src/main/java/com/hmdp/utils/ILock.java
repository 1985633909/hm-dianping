package com.hmdp.utils;

/**
 *
 * <p>
 *
 * </p>
 *
 * @author 19856
 * @since 2022/12/13-13:46
 * @description
 */
public interface ILock {

    //获取锁
    boolean tryLock(long timeoutSec);

    //释放锁
    void unlock();
}
