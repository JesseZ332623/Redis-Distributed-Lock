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

    /** Redis 分布式锁相关属性配置 */
    private DistributedLock distributedLockProperties
        = new DistributedLock();

    /** Redis 公平信号量相关属性配置（暂无）*/
    private FairSemaphore fairSemaphoreProperties
        = new FairSemaphore();

    @Data
    @NoArgsConstructor
    public static class DistributedLock
    {
        /** 分布式锁键的键头（用户自定义，默认为 lock）。*/
        private String lockKeyHead = "lock";
    }

    @Data
    @NoArgsConstructor
    public static class FairSemaphore {}
}