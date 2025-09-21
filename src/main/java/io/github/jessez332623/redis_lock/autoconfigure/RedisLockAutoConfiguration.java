package io.github.jessez332623.redis_lock.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jessez332623.redis_lock.distributed_lock.RedisDistributedLock;
import io.github.jessez332623.redis_lock.distributed_lock.impl.DefaultRedisDistributedLockImpl;
import io.github.jessez332623.redis_lock.fair_semaphore.RedisFairSemaphore;
import io.github.jessez332623.redis_lock.fair_semaphore.impl.DefaultRedisFairSemaphoreImpl;
import io.github.jessez332623.redis_lock.utils.LuaOperatorResult;
import io.github.jessez332623.redis_lock.utils.LuaScriptReader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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

/** Redis Lock Spring 自动配置类。*/
@Configuration
@ConditionalOnProperty(
    prefix         = "app.redis-lock",
    name           = "enabled",
    havingValue    = "true",
    matchIfMissing = true   // 默认启用本依赖
)
@EnableConfigurationProperties(RedisLockProperties.class)
public class RedisLockAutoConfiguration
{
    /** Redis Lock 专用的、用于执行 Lua 脚本的 ReactiveRedisTemplate。*/
    @Bean
    @ConditionalOnBean(
        name = {
            "executeLuaScriptReactiveRedisConnectionFactory",
            "redisLockObjectMapper"
        }
    )
    public ReactiveRedisTemplate<String, LuaOperatorResult>
    scriptRedisTemplate(
        @Qualifier("executeLuaScriptReactiveRedisConnectionFactory")
        ReactiveRedisConnectionFactory factory,
        @Qualifier("redisLockObjectMapper")
        ObjectMapper objectMapper
    )
    {
        RedisSerializer<String> keySerializer = new StringRedisSerializer();

        Jackson2JsonRedisSerializer<LuaOperatorResult> valueSerializer
            = new Jackson2JsonRedisSerializer<>(objectMapper, LuaOperatorResult.class);

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

    /** Redis Lock 专用的 ObjectMapper。*/
    @Bean("redisLockObjectMapper")
    public ObjectMapper redisLockObjectMapper()
    {
       return new ObjectMapper();
    }

    /** Redis Lock 专用的 Lua 脚本读取器 Bean。*/
    @Bean
    @Contract(" -> new")
    public @NotNull LuaScriptReader
    luaScriptReader() {
        return new LuaScriptReader();
    }

    @Bean
    @ConditionalOnMissingBean(DefaultRedisDistributedLockImpl.class)
    public RedisDistributedLock
    redisDistributedLock(
        @NotNull
        RedisLockProperties properties,
        ReactiveRedisTemplate<String, LuaOperatorResult> scriptRedisTemplate,
        LuaScriptReader luaScriptReader
    )
    {
        return new
        DefaultRedisDistributedLockImpl(
            properties.getDistributedLockProperties().getLockKeyHead(),
            luaScriptReader,
            scriptRedisTemplate
        );
    }

    @Bean
    @ConditionalOnMissingBean(DefaultRedisFairSemaphoreImpl.class)
    public RedisFairSemaphore
    redisFairSemaphore(
        @NotNull
        RedisLockProperties properties,
        ReactiveRedisTemplate<String, LuaOperatorResult> scriptRedisTemplate,
        LuaScriptReader luaScriptReader
    )
    {
        return new
        DefaultRedisFairSemaphoreImpl(
            luaScriptReader,
            scriptRedisTemplate
        );
    }
}
