package io.github.jessez332623.redis_lock.statistics.impl;

import io.github.jessez332623.redis_lock.statistics.StatisticalInstrument;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/** Redis 分布式锁故障统计器。*/
@Slf4j
public class DistributedLockFaultStatistical implements StatisticalInstrument
{
    /** 锁超时次数。*/
    private final
    AtomicLong lockTimeoutCount = new AtomicLong(0L);

    /** 释放锁但锁不存在次数。*/
    private final
    AtomicLong lockNotExistCount = new AtomicLong(0L);

    /** 同步释放锁次数（罕见）。*/
    private final
    AtomicLong concurrentReleaseCount = new AtomicLong(0L);

    /** 尝试释放别人的锁次数。*/
    private final
    AtomicLong releaseOthersCount = new AtomicLong(0L);

    /** 锁超时次数 + 1 */
    public void increaseLockTimeout() {
        this.lockTimeoutCount.incrementAndGet();
    }

    /** 释放锁但锁不存在次数 + 1 */
    public void increaseLockNotExist() {
        this.lockNotExistCount.incrementAndGet();
    }

    /** 同步释放锁次数 + 1 */
    public void increaseConcurrentRelease() {
        this.concurrentReleaseCount.incrementAndGet();
    }

    /** 尝试释放别人的锁次数 + 1 */
    public void increaseReleaseOthers() {
        this.releaseOthersCount.incrementAndGet();
    }

    /** 获取统计结果字符串。*/
    @Override
    public String getStatisticResultString()
    {
        return
        String.format(
            "Acquire lock timeout: %d, lock not exist: %d, " +
            "concurrent release: %s, try release others: %d",
            this.lockTimeoutCount.get(),
            this.lockNotExistCount.get(),
            this.concurrentReleaseCount.get(),
            this.releaseOthersCount.get()
        );
    }

    /** 获取统计结果实例。*/
    @Override
    public DistributedLockFaultStatistical
    getStatisticResultInstance() {
        return this;
    }

    /** 清理统计结果（选择性实现）*/
    @Override
    public void cleanStatisticResult()
    {
        this.lockTimeoutCount.set(0);
        this.lockNotExistCount.set(0);
        this.concurrentReleaseCount.set(0);
        this.releaseOthersCount.set(0);
    }

    /** 输出统计结果（默认由 printf 输出）*/
    @Override
    public void displayStatisticResult() {
        log.debug("{}", this.getStatisticResultString());
    }
}