package io.github.jessez332623.redis_lock.statistics;

/** 各个分布式锁实现的故障统计器，由各个分布式锁来实现。*/
public interface StatisticalInstrument
{
    /** 获取统计结果字符串。*/
    String getStatisticResultString();

    /** 获取统计结果实例。*/
    StatisticalInstrument getStatisticResultInstance();

    /** 清理统计结果（选择性实现）*/
    default void cleanStatisticResult() {
        throw new UnsupportedOperationException();
    }

    /** 输出统计结果（默认由 printf 输出）*/
    default void displayStatisticResult() {
        System.out.println(this.getStatisticResultString());
    }
}