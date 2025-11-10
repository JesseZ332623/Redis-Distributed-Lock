package io.github.jessez332623.redis_lock.fair_semaphore.impl;

import io.github.jessez332623.redis_lock.error_handle.RedisLockErrorHandle;
import io.github.jessez332623.redis_lock.fair_semaphore.exception.AcquireSemaphoreFailed;
import io.github.jessez332623.redis_lock.fair_semaphore.exception.SemaphoreNotFound;
import io.github.jessez332623.redis_lock.utils.LuaOperatorResult;
import io.github.jessez332623.redis_lock.utils.LuaScriptReader;
import io.github.jessez332623.redis_lock.fair_semaphore.RedisFairSemaphore;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static io.github.jessez332623.redis_lock.utils.LuaScriptOperatorType.FAIR_SEMAPHORE;
import static java.lang.String.format;

/** Redis 公平信号量默认实现。*/
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultRedisFairSemaphoreImpl implements RedisFairSemaphore
{
    /** 公平信号量键的键前缀（用户自定义）。*/
    private String FAIR_SEMAPHORE_KEY_PREFIX;

    /** Lua 脚本读取器。*/
    private LuaScriptReader luaScriptReader;

    /** 执行 Lua 脚本专用 Redis 模板。*/
    private
    ReactiveRedisTemplate<String, LuaOperatorResult> scriptRedisTemplate;

    /** Redis Lock 专用的线程调度器。*/
    private Scheduler scheduler;

    /** 公共有参构造函数，满足 Spring 自动装配之需要。*/
    public DefaultRedisFairSemaphoreImpl(
        String fairSemaphoreKeyPrefix,
        LuaScriptReader scriptReader,
        ReactiveRedisTemplate<String, LuaOperatorResult> redisScriptTemplate,
        Scheduler scheduler
    )
    {
        this.FAIR_SEMAPHORE_KEY_PREFIX = fairSemaphoreKeyPrefix;
        this.luaScriptReader           = scriptReader;
        this.scriptRedisTemplate       = redisScriptTemplate;
        this.scheduler                 = scheduler;
    }

    /** 组合信号量有序集合键。*/
    @Contract(pure = true)
    private @NotNull String
    getSemaphoreNameKey(String semaphoreName) {
        return FAIR_SEMAPHORE_KEY_PREFIX + "{" + semaphoreName + "}";
    }

    /** 组合信号量拥有者有序集合键。*/
    @Contract(pure = true)
    private @NotNull String
    getSemaphoreOwnerKey(String semaphoreName)
    {
        return
        FAIR_SEMAPHORE_KEY_PREFIX + "{" + semaphoreName + "}:" + "owner";
    }

    /** 组合信号量全局计数器数据键。*/
    @Contract(pure = true)
    private @NotNull String
    getSemaphoreCounterKey(String semaphoreName)
    {
        return
        FAIR_SEMAPHORE_KEY_PREFIX + "{" + semaphoreName + "}:" + "counter";
    }

    /**
     * 进程尝试获取一个信号量。
     *
     * @param semaphoreName 信号量键名
     * @param limit         最大信号量值
     * @param timeout       信号量有效期（单位：秒）
     *
     * @return 发布信号量唯一标识符的 Mono
     */
    private @NotNull Mono<String>
    acquireFairSemaphore(String semaphoreName, long limit, long timeout)
    {
        final String semaphoreNameKey
            = getSemaphoreNameKey(semaphoreName);

        final String semaphoreOwnerKey
            = getSemaphoreOwnerKey(semaphoreName);

        final String semaphoreCounterKey
            = getSemaphoreCounterKey(semaphoreName);

        final String identifier
            = UUID.randomUUID().toString();

        return
        this.luaScriptReader
            .read(FAIR_SEMAPHORE, "acquireFairSemaphore.lua")
            .flatMap((script) ->
                this.scriptRedisTemplate
                    .execute(
                        script,
                        List.of(semaphoreNameKey, semaphoreOwnerKey, semaphoreCounterKey),
                        limit, timeout, identifier)
                    .timeout(Duration.ofSeconds(5L))
                    .next()
                    .subscribeOn(this.scheduler)
                    .flatMap((result) ->
                        switch (result.getResult())
                        {
                            case "ACQUIRE_SEMAPHORE_FAILED" ->
                                Mono.error(
                                    new AcquireSemaphoreFailed(
                                        "Acquire semaphore failed! Caused by: The resource is busy."
                                    )
                                );

                            case "SUCCESS" ->
                                Mono.just(identifier);

                            case null, default ->
                                Mono.error(
                                    new IllegalStateException(
                                        "Unexpected value: " + result.getResult()
                                    )
                                );
                        }
                    )
            ).onErrorResume(RedisLockErrorHandle::redisLockGenericErrorHandle);
    }

    /**
     * 进程为了长期持有信号量，需要定期的对信号量进行刷新。
     *
     * @param semaphoreName 信号量键名
     * @param identifier    信号量唯一标识符
     *
     * @return 不发布任何数据的 Mono，表示操作是否完成
     */
    private @NotNull Mono<Void>
    refreshFairSemaphore(String semaphoreName, String identifier)
    {
        final String semaphoreNameKey
            = getSemaphoreNameKey(semaphoreName);

        return
        this.luaScriptReader
            .read(FAIR_SEMAPHORE,"refreshFairSemaphore.lua")
            .flatMap((script) ->
                this.scriptRedisTemplate
                    .execute(script, List.of(semaphoreNameKey), identifier)
                    .timeout(Duration.ofSeconds(3L))
                    .next()
                    .subscribeOn(this.scheduler)
                    .flatMap((result) ->
                        switch (result.getResult())
                        {
                            case "SEMAPHORE_NOT_FOUND" ->
                                Mono.error(
                                    new SemaphoreNotFound(
                                        format(
                                            "Fair semaphore %s not exist in %s",
                                            identifier, semaphoreName
                                        )
                                    )
                                );

                            case "SUCCESS" -> Mono.empty();

                            case null, default ->
                                Mono.error(
                                    new IllegalStateException(
                                        "Unexpected value: " + result.getResult()
                                    )
                                );
                            }
                        )
            ).onErrorResume(RedisLockErrorHandle::redisLockGenericErrorHandle).then();
    }

    /**
     * 进程尝试释放一个信号量。
     *
     * @param semaphoreName 信号量键名
     * @param identifier    信号量唯一标识符
     *
     * @return 不发布任何数据的 Mono，表示操作是否完成
     */
    private @NotNull Mono<Void>
    releaseFairSemaphore(String semaphoreName, String identifier)
    {
        final String semaphoreNameKey
            = getSemaphoreNameKey(semaphoreName);

        final String semaphoreOwnerKey
            = getSemaphoreOwnerKey(semaphoreName);

        return
        this.luaScriptReader
            .read(FAIR_SEMAPHORE, "releaseFairSemaphore.lua")
            .flatMap((script) ->
                this.scriptRedisTemplate
                    .execute(
                        script,
                        List.of(semaphoreNameKey, semaphoreOwnerKey),
                        identifier)
                    .timeout(Duration.ofSeconds(3L))
                    .next()
                    .subscribeOn(this.scheduler)
                    .flatMap((result) ->
                        switch (result.getResult())
                        {
                            case "SEMAPHORE_TIMEOUT" ->
                                Mono.error(
                                    new SemaphoreNotFound(
                                        format("Semaphore: %s timeout.", identifier)
                                    )
                                );

                            case "SUCCESS" -> Mono.empty();

                            case null, default ->
                                Mono.error(
                                    new IllegalStateException(
                                        "Unexpected value: " + result.getResult()
                                    )
                                );
                        }
                    )
            ).onErrorResume(RedisLockErrorHandle::redisLockGenericErrorHandle).then();
    }

    /**
     * 兼容响应式流的 Redis 公平信号量操作，
     * 使用 {@link Mono#usingWhen(Publisher, Function, Function)} 方法，在业务逻辑（action）范围前后，
     * 自动完成信号量的获取与释放操作。
     *
     * @param <T> 在信号量作用域中业务逻辑返回的类型
     *
     * @param semaphoreName 信号量键名（例 semaphore:remote）
     * @param limit         最大信号量值
     * @param timeout       信号量有效期（单位：秒）
     * @param action        业务逻辑
     *
     * @return 发布业务逻辑执行结果数据的 Mono
     */
    @Override
    public <T> Mono<T>
    withFairSemaphore(
        String semaphoreName,
        long limit, long timeout,
        Function<String, Mono<T>> action)
    {
        final String semaphoreNameKey = getSemaphoreNameKey(semaphoreName);

        return
        Mono.defer(() ->
            Mono.usingWhen(
                this.acquireFairSemaphore(semaphoreNameKey, limit, timeout)
                    .map((identifier) -> identifier),
                (identifier) -> {
                    Mono<T> actionMono = action.apply(identifier);

                    // 对持有信号量时间较长的进程，才提供刷新功能
                    if (timeout > 10)
                    {
                        // 刷新间隔为超时时间的一半
                        Duration refreshInterval
                            = Duration.ofSeconds(timeout / 2);

                        /*
                         * 这里出现了几个复杂的响应式流操作，需要做出说明：
                         *
                         * 1. delayUntil() 延迟调用这个操作的流，直到提供给这个操作的的流
                         *   （此处是在长时间业务执行完毕前不断刷新信号量的操作）执行完毕，
                         *    才允许 actionMono 的完成信号向下游传播。
                         *
                         * 2. Flux.interval() 每间隔一段时间，递增然后发布一个 Long 值
                         *
                         * 3. takeUntilOther() 有条件的 take 操作，
                         *    直到收到业务逻辑 actionMono 执行完毕的信号
                         *    即 actionMono.ignoreElement().then(Mono.empty())，
                         *    才停止 Flux.interval() 的发布（业务执行完毕，停止刷新信号量）。
                         *
                         * 4. concatMap() 将每一个 Flux.interval() 发布的值映射为
                         *    refreshFairSemaphore() 操作，并按顺序执行。
                         *
                         * 5. then() 我们不关心刷新的结果，只关系刷新是否成功完成
                         */
                        return
                        actionMono.delayUntil((value) ->
                            Flux.interval(refreshInterval)
                                .takeUntilOther(
                                    actionMono.ignoreElement().then(Mono.empty()))
                                .concatMap((ignore) ->
                                     this.refreshFairSemaphore(semaphoreNameKey, identifier))
                                .then()
                        );
                    }

                    return actionMono;
                },
                (identifier) ->
                    this.releaseFairSemaphore(semaphoreNameKey, identifier)
            )
        );
    }
}
