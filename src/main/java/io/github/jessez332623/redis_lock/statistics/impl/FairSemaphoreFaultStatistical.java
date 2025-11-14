package io.github.jessez332623.redis_lock.statistics.impl;

import io.github.jessez332623.redis_lock.statistics.StatisticalInstrument;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/** Redis 公平信号量故障统计器。*/
@Slf4j
public class FairSemaphoreFaultStatistical implements StatisticalInstrument
{
    /** 获取信号量失败次数。*/
    private final
    AtomicLong acquireSemaphoreFailedCount = new AtomicLong(0L);

    /** 信号量不存在次数。*/
    private final
    AtomicLong semaphoreNotFoundCount = new AtomicLong(0L);

    /** 获取信号量超时次数。*/
    private final
    AtomicLong semaphoreTimeoutCount = new AtomicLong(0L);

    /** 获取信号量失败次数 + 1 */
    public void increaseAcquireFailed() {
        this.acquireSemaphoreFailedCount.incrementAndGet();
    }

    /** 信号量不存在次数 + 1 */
    public void increaseNotFound() {
        this.semaphoreNotFoundCount.incrementAndGet();
    }

    /** 获取信号量超时次数 + 1 */
    public void increaseTimeout() {
        this.semaphoreTimeoutCount.incrementAndGet();
    }

    /** 获取统计结果字符串。*/
    @Override
    public String getStatisticResultString()
    {
        return
        String.format(
            "Acquire semaphore failed: %d, " +
            "semaphore not found: %d, semaphore timeout: %d",
            this.acquireSemaphoreFailedCount.get(),
            this.semaphoreNotFoundCount.get(),
            this.semaphoreTimeoutCount.get()
        );
    }

    /** 获取统计结果实例。*/
    @Override
    public FairSemaphoreFaultStatistical
    getStatisticResultInstance() {
        return this;
    }

    /** 清理统计结果（选择性实现）*/
    @Override
    public void cleanStatisticResult()
    {
        this.acquireSemaphoreFailedCount.set(0);
        this.semaphoreNotFoundCount.set(0);
        this.semaphoreTimeoutCount.set(0);
    }

    /** 输出统计结果（默认由 printf 输出）*/
    @Override
    public void displayStatisticResult() {
        log.debug("{}", this.getStatisticResultString());
    }
}
