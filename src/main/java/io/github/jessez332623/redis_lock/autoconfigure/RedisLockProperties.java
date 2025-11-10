package io.github.jessez332623.redis_lock.autoconfigure;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring 依赖自动配置属性类。*/
@Data
@ConfigurationProperties(prefix = "app.redis-lock")
public class RedisLockProperties
{
    /** 是否启用本依赖？（默认启用）*/
    private boolean enabled = true;

    /** 项目 I/O 密集型操作通用调度器相关属性配置。*/
    private ProjectSchedulersProperties schedulers
        = new ProjectSchedulersProperties();

    /** Redis 分布式锁相关属性配置 */
    private DistributedLockProperties distributedLock
        = new DistributedLockProperties();

    /** Redis 公平信号量相关属性配置 */
    private FairSemaphoreProperties fairSemaphore
        = new FairSemaphoreProperties();

    @Data
    @NoArgsConstructor
    public static class DistributedLockProperties
    {
        /** 分布式锁键的键前缀（用户自定义，默认为 lock）。*/
        private String keyPrefix = "lock";
    }

    @Data
    @NoArgsConstructor
    public static class FairSemaphoreProperties
    {
        /** 公平信号量键的键前缀（用户自定义，默认为 semaphore）。*/
        private String keyPrefix = "semaphore";
    }

    @Data
    @NoArgsConstructor
    public static class ProjectSchedulersProperties
    {
        /** 最大线程数（默认 100 线程）*/
        private int maxThreads = 100;

        /** 任务队列容量（默认 1000）*/
        private int taskQueueCapacity = 1000;

        /** 调度线程名（默认 redis-lock）*/
        private String threadName = "redis-lock";

        /** 空闲线程存活时间（默认 60 秒）*/
        private int ttlSeconds = 60;

        /* 是否开启守护线程？（对于分布式锁操作来说是必须的）*/
        private boolean daemon = true;
    }
}