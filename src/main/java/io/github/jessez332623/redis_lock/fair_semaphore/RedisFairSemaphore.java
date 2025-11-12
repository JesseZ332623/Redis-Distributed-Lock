package io.github.jessez332623.redis_lock.fair_semaphore;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

/** Redis 公平信号量接口。*/
public interface RedisFairSemaphore
{
    /**
     * 兼容响应式流的 Redis 公平信号量操作，
     * 使用 {@link Mono#usingWhen(Publisher, Function, Function)} 方法，
     * 在业务逻辑（action）范围前后，自动完成信号量的获取与释放操作。
     *
     * @param <T> 在信号量作用域中业务逻辑返回的类型
     *
     * @param semaphoreName 信号量键名
     * @param limit         最大信号量值
     * @param timeout       信号量有效期（毫秒级别）
     * @param action        业务逻辑
     *
     * @return 发布业务逻辑执行结果数据的 {@link Mono}
     */
    <T> Mono<T>
    withFairSemaphore(
        String semaphoreName,
        long limit, Duration timeout,
        Function<String, Mono<T>> action
    );
}