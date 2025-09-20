package io.github.jessez332623.redis_lock.distributed_lock.impl;

import io.github.jessez332623.redis_lock.error_handle.RedisLockErrorHandle;
import io.github.jessez332623.redis_lock.distributed_lock.RedisDistributedLock;
import io.github.jessez332623.redis_lock.distributed_lock.exception.RedisDistributedLockAcquireTimeout;
import io.github.jessez332623.redis_lock.utils.LuaOperatorResult;
import io.github.jessez332623.redis_lock.utils.LuaScriptReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static io.github.jessez332623.redis_lock.utils.LuaScriptOperatorType.DISTRIBUTE_LOCK;
import static java.lang.String.format;

/**
 * <p>Redis 分布式锁默认实现类。</p>
 *
 * <strong>
 *     需要强调的是，分布式锁是协调多个访问同一个 Redis 服务的客户端使用的锁，
 *     不要和用于控制同一个进程的多个线程之间同步的锁
 *     （比如 Java 自带的 synchronized）搞混了（笑）。
 * </strong>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultRedisDistributedLockImpl implements RedisDistributedLock
{
    /** 分布式锁键的键头（用户自定义）。*/
    private String LOCK_KEY;

    /** Lua 脚本读取器。*/
    private LuaScriptReader luaScriptReader;

    /** 执行 Lua 脚本专用 Redis 模板。*/
    private
    ReactiveRedisTemplate<String, LuaOperatorResult> scriptRedisTemplate;

    /** 公共有参构造函数，满足 Spring 自动装配之需要。*/
    public DefaultRedisDistributedLockImpl(
        String lockKey,
        LuaScriptReader luaScriptReader,
        ReactiveRedisTemplate<String, LuaOperatorResult> scriptRedisTemplate
    )
    {
        this.LOCK_KEY            = lockKey;
        this.luaScriptReader     = luaScriptReader;
        this.scriptRedisTemplate = scriptRedisTemplate;
    }

    /** 组合 Redis 锁键，LOCK_KEY 键头用户可以自定义。*/
    @Contract(pure = true)
    private @NotNull String
    getRedisLockKey(String keyName) {
        return LOCK_KEY + ":" + keyName;
    }

    /**
     * 尝试获取一个锁。
     *
     * @param lockName          锁名
     * @param acquireTimeout    获取锁的时间期限
     * @param lockTimeout       锁本身的有效期（单位：秒）
     *
     * @return 返回一个 Mono，成功获取锁时发布锁的唯一标识符（UUID）
     */
    private @NotNull Mono<String>
    acquireLockTimeout(
        String lockName, long acquireTimeout, long lockTimeout)
    {
        final String lockKeyName = getRedisLockKey(lockName);
        final String identifier  = UUID.randomUUID().toString();

        return
        this.luaScriptReader
            .read(DISTRIBUTE_LOCK, "acquireLockTimeout.lua")
            .flatMap((script) ->
                this.scriptRedisTemplate
                    .execute(
                        script,
                        List.of(lockKeyName),
                        identifier, acquireTimeout, lockTimeout)
                    .next()
                    .flatMap((result) ->
                        switch (result.getResult())
                        {
                            case "GET_LOCK_TIMEOUT" ->
                                Mono.error(
                                    new RedisDistributedLockAcquireTimeout(
                                        format(
                                            "Acquire lock: %s timeout! (acquireTimeout = %d seconds)",
                                            lockName, acquireTimeout
                                        )
                                    )
                                );

                            case "SUCCESS" -> {
                                log.info("Lock obtain success!");
                                yield Mono.just(identifier);
                            }

                            case null, default ->
                                Mono.error(
                                    new IllegalStateException(
                                        "Unexpected value: " + result.getResult()
                                    )
                                );
                        }
                    )
            ).onErrorResume(RedisLockErrorHandle::redisLockGenericErrorHandel);
    }

    /**
     * 尝试释放一个锁。
     *
     * @return 不发布任何数据的 Mono，表示操作整体是否完成
     */
    private @NotNull Mono<Void>
    releaseLock(String lockName, String identifier)
    {
        final String lockKeyName = getRedisLockKey(lockName);

        return
        this.luaScriptReader
            .read(DISTRIBUTE_LOCK, "releaseLock.lua")
            .flatMap((script) ->
                this.scriptRedisTemplate
                    .execute(script, List.of(lockKeyName), identifier)
                    .next()
                    .flatMap((result) ->
                        switch (result.getResult())
                        {
                            case "LOCK_NOT_EXIST" -> {
                                log.error("Lock (identifier = {}) not exists!", identifier);
                                yield Mono.empty();
                            }

                            case "CONCURRENT_DELETE" -> {
                                log.warn("Concurrent delete happened!");
                                yield Mono.empty();
                            }

                            case "LOCK_OWNED_BY_OTHERS" -> {
                                log.error("Try to delete others lock!");
                                yield Mono.empty();
                            }

                            case "SUCCESS" -> {
                                log.info("Lock release success!");
                                yield Mono.empty();
                            }

                            case null, default ->
                                Mono.error(
                                    new IllegalStateException(
                                        "Unexpected value: " + result.getResult()
                                    )
                                );
                        }
                    )
            ).onErrorResume(RedisLockErrorHandle::redisLockGenericErrorHandel).then();
    }

    /**
     * 兼容响应式流的 Redis 分布式锁操作，
     * 使用 {@link Mono#usingWhen(Publisher, Function, Function)} 方法，
     * 在业务逻辑（action）范围前后，自动完成信号量的获取与释放操作。
     *
     * @param <T> 在锁作用域中业务逻辑返回的类型
     *
     * @param lockName       锁名
     * @param acquireTimeout 获取锁的实现期限
     * @param lockTimeout    锁本身的持有时间期限
     * @param action         业务逻辑
     *
     * @return 发布业务逻辑执行结果数据的 {@link Mono}
     */
    @Override
    public <T> Mono<T>
    withLock(
        String lockName,
        long acquireTimeout, long lockTimeout,
        Function<String, Mono<T>> action)
    {
        /*
         * 注意外部再用 defer() 包一层，
         * 确保每次调用都创建新的响应式流。
         */
        return
        Mono.defer(() ->
            Mono.usingWhen(
                this.acquireLockTimeout(lockName, acquireTimeout, lockTimeout)
                    .map(acquiredId -> acquiredId),
                action,
                (acquiredId) ->
                    this.releaseLock(lockName, acquiredId)
            )
        );
    }
}