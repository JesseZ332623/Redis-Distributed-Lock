package io.github.jessez332623.redis_lock.fair_semaphore.exception;

/** 按唯一标识符没有找到对应信号量时，抛出本异常。*/
public class SemaphoreNotFound extends RuntimeException
{
    public SemaphoreNotFound(String message) {
        super(message);
    }
    public SemaphoreNotFound(String message, Throwable throwable) {
        super(message, throwable);
    }
}