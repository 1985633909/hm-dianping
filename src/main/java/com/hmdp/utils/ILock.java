package com.hmdp.utils;

/**
 * @author: 19856
 * @date: 2022/12/13-13:46
 * @description:
 */
public interface ILock {

    //获取锁
    boolean tryLock(long timeoutSec);

    //释放锁
    void unlock();
}
