package io.github.jessez332623.redis_lock.error_handle;

import io.github.jessez332623.redis_lock.fair_semaphore.exception.SemaphoreNotFound;import io.github.jessez332623.redis_lock.utils.exception.LuaScriptOperatorFailed;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** 本项目所有的 Redis 操作中，通用的错误处理方法工具类。*/
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final public class RedisLockErrorHandle
{
    /**
     * 本项目所有的 Redis 操作中，通用的错误处理方法。
     *
     * @param <T> 要发布数据或异常类型
     *
     * @param exception Redis 操作中可能抛出的异常
     *
     * @return 发布异常或者默认值的 Mono
     */
    public static <T> @NotNull Mono<T>
    redisLockGenericErrorHandle(@NotNull Throwable exception)
    {
        if (isLettuceTimeoutException(exception)) {
            return Mono.error(exception);
        }

        switch (exception)
        {
            case LuaScriptOperatorFailed luaScriptOperatorFailed ->
                log.error("{}", luaScriptOperatorFailed.getMessage());

            case SemaphoreNotFound semaphoreNotFound ->
                log.error("{}", semaphoreNotFound.getMessage());

            case RedisConnectionFailureException redisConnectionFailureException ->
                log.error(
                    "Redis connect failed!", redisConnectionFailureException);

            case SerializationException serializationException ->
                log.error(
                    "Data deserialization failed!", serializationException);

            case DataAccessException dataAccessException ->
            {
                final String errorMessage
                    = dataAccessException.getMessage();

                // 检查是不是其他客户端抛出的命令超时异常
                if (Objects.nonNull(errorMessage) && errorMessage.contains("timeout")) {
                    log.warn("(Genric) Redis operator timeout!", exception);
                }

                log.error("Spring data access failed!", exception);
            }

            default -> {}
        }

        return Mono.error(exception);
    }

    /**
     * 我不应该只为了接住 Lettuce 依赖下的
     * io.lettuce.core.RedisCommandTimeoutException 异常
     * 而引入整个 spring-boot-starter-data-redis-reactive，这对于一个上传到 Maven 中央仓库的依赖来说是不友好的。
     * 所以这里需要用反射大法来检查用户所用的 Redis 客户端是否为 Lettuce，
     * 如果是 Lettuce 就处理这个客户端下的异常，
     * 反之 Spring 会把其他客户端抛出的超时异常包装成 {@link DataAccessException}，
     * 届时在那里进行相关处理即可。
     *
     * @param exception 上层抛出的异常
     *
     * @return 是否为 Lettuce 客户端特有的超时异常？
     */
    private static boolean
    isLettuceTimeoutException(Throwable exception)
    {
        boolean isLettuceTimeout = false;
        try
        {
            Class<?> timeoutClass
                = Class.forName("io.lettuce.core.RedisCommandTimeoutException");

            if (timeoutClass.isInstance(exception))
            {
                isLettuceTimeout = true;
                log.warn("(Lettuce) Redis operator timeout!", exception);
                return isLettuceTimeout;
            }
        }
        catch (ClassNotFoundException classNotFound) {
            // 倘若没有使用 Lettuce 客户端
            return false;
        }

        return isLettuceTimeout;
    }
}