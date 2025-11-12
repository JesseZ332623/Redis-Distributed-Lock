package io.github.jessez332623.redis_lock.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.jessez332623.redis_lock.distributed_lock.RedisDistributedLock;
import io.github.jessez332623.redis_lock.distributed_lock.impl.DefaultRedisDistributedLockImpl;
import io.github.jessez332623.redis_lock.fair_semaphore.RedisFairSemaphore;
import io.github.jessez332623.redis_lock.fair_semaphore.impl.DefaultRedisFairSemaphoreImpl;
import io.github.jessez332623.redis_lock.utils.LuaOperatorResult;
import io.github.jessez332623.redis_lock.utils.LuaScriptReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Redis-Lock Spring 自动配置类。*/
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix         = "app.redis-lock",
    name           = "enabled",
    havingValue    = "true",
    matchIfMissing = true   // 默认启用本依赖
)
@EnableConfigurationProperties(RedisLockProperties.class)
@ConditionalOnClass({
    ReactiveRedisTemplate.class,
    ReactiveRedisConnectionFactory.class
})
public class RedisLockAutoConfiguration
{
    /** Redis Lock 专用的 Lua 脚本读取器 Bean。*/
    @Bean
    public LuaScriptReader
    luaScriptReader() {
        return new LuaScriptReader();
    }

    /** Redis Lock 专用的线程调度器 Bean。*/
    @Bean(name = "distributedLockScheduler")
    public Scheduler
    distributedLockScheduler(RedisLockProperties properties)
    {
        RedisLockProperties.ProjectSchedulersProperties
            schedulersProperties = properties.getSchedulers();

        return
        Schedulers.newBoundedElastic(
            schedulersProperties.getMaxThreads(),
            schedulersProperties.getTaskQueueCapacity(),
            schedulersProperties.getThreadName(),
            schedulersProperties.getTtlSeconds(),
            schedulersProperties.isDaemon()
        );
    }

    /**
     * Redis-Lock 依赖专用的、用于执行 Lua 脚本的 ReactiveRedisTemplate。
     * 使用者需要在自己项目中配置一个正确的、实现了 {@link ReactiveRedisConnectionFactory} 实例，
     * 反之本依赖的所有 Bean 会在应用程序启动时创建失败。
     *
     * @see <a href="https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/README.md">配置 Lettuce 客户端的连接工厂</a>
     */
    @Bean
    @ConditionalOnBean(ReactiveRedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "redisLockScriptTemplate")
    public ReactiveRedisTemplate<String, LuaOperatorResult>
    redisLockScriptTemplate(ReactiveRedisConnectionFactory factory)
    {
        try
        {
            RedisSerializer<String> keySerializer = new StringRedisSerializer();

            Jackson2JsonRedisSerializer<LuaOperatorResult> valueSerializer
                = new Jackson2JsonRedisSerializer<>(
                new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                LuaOperatorResult.class
            );

            RedisSerializationContext<String, LuaOperatorResult> context
                = RedisSerializationContext.<String, LuaOperatorResult>
                    newSerializationContext(keySerializer)
                .value(valueSerializer)
                .hashKey(keySerializer)
                .hashValue(valueSerializer)
                .build();

            return new
            ReactiveRedisTemplate<>(factory, context);
        }
        catch (Exception exception)
        {
            log.error(
                "Failed to create RedisLockScriptTemplate caused by: {}",
                exception.getMessage()
            );

            throw new
            IllegalStateException("RedisLockScriptTemplate init failed!", exception);
        }
    }

    /** Redis 分布式锁的自动装配方法。*/
    @Bean
    @ConditionalOnMissingBean(RedisDistributedLock.class)
    public RedisDistributedLock
    redisDistributedLock(
        RedisLockProperties properties,
        ReactiveRedisTemplate<String, LuaOperatorResult> redisLockScriptTemplate,
        LuaScriptReader luaScriptReader,
        @Qualifier("distributedLockScheduler") Scheduler scheduler
    )
    {
        return new
        DefaultRedisDistributedLockImpl(
            properties.getDistributedLock().getKeyPrefix(),
            luaScriptReader,
            redisLockScriptTemplate,
            scheduler,
            properties.getOperationTimeout()
        );
    }

    /** Redis 分布式公平信号量自动装配方法。*/
    @Bean
    @ConditionalOnMissingBean(RedisFairSemaphore.class)
    public RedisFairSemaphore
    redisFairSemaphore(
        RedisLockProperties properties,
        ReactiveRedisTemplate<String, LuaOperatorResult> redisLockScriptTemplate,
        LuaScriptReader luaScriptReader,
        @Qualifier("distributedLockScheduler") Scheduler scheduler
    )
    {
        return new
        DefaultRedisFairSemaphoreImpl(
            properties.getFairSemaphore().getKeyPrefix(),
            luaScriptReader,
            redisLockScriptTemplate,
            scheduler,
            properties.getOperationTimeout()
        );
    }
}