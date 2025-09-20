package io.github.jessez332623.redis_lock.distributed_lock.exception;

/** 当等待获取 Redis 锁超过设定的时间限制，抛本异常。*/
public class RedisDistributedLockAcquireTimeout extends RuntimeException
{
    public RedisDistributedLockAcquireTimeout(String message) {
        super(message);
    }
    public RedisDistributedLockAcquireTimeout(String message, Throwable throwable) {
        super(message, throwable);
    }
}