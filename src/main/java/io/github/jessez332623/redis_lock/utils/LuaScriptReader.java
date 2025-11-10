package io.github.jessez332623.redis_lock.utils;

import io.github.jessez332623.redis_lock.utils.exception.LuaScriptOperatorFailed;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.lang.String.format;

/** Redis Lua 脚本读取器实现。*/
@Slf4j
@Component
@NoArgsConstructor(access = AccessLevel.PUBLIC)
final public class LuaScriptReader
{
    /** 本项目 Lua 脚本根目录。*/
    private static final String
    LUA_SCRIPT_CLASSPATH_PREFIX = "lua-script";

    /** Lua 脚本缓存：operatorType -> (scriptName -> script) */
    private final ConcurrentMap<
        LuaScriptOperatorType,
        ConcurrentMap<String, DefaultRedisScript<LuaOperatorResult>>>
        scriptCache = new ConcurrentHashMap<>();

    @Autowired
    @Qualifier("distributedLockScheduler")
    private Scheduler scheduler;

    /** 从 classpath 中加载脚本。*/
    @Contract("_, _ -> new")
    private @NotNull
    DefaultRedisScript<LuaOperatorResult>
    loadFromClassPath(@NotNull LuaScriptOperatorType operatorType, String luaScriptName) throws IOException
    {
        // 拼接 Lua 脚本的 classpath，
        // 格式：lua-script/{operator-type}/{script-name.lua}
        final String scriptClasspath
            = LUA_SCRIPT_CLASSPATH_PREFIX       +
              "/" + operatorType.getTypeName()  +
              "/" + luaScriptName;

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(scriptClasspath))
        {
            if (inputStream == null)
            {
                throw new
                LuaScriptOperatorFailed(
                    format(
                        "Lua script: %s not found in classpath: %s!",
                        luaScriptName, scriptClasspath
                    )
                );
            }

            String scriptContent
                = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            return new
            DefaultRedisScript<>(scriptContent, LuaOperatorResult.class);
        }
    }

    /** 获取或者创建指定操作类型的脚本缓存。*/
    private ConcurrentMap<String, DefaultRedisScript<LuaOperatorResult>>
    getOrCreateScriptCache(LuaScriptOperatorType scriptOperatorType)
    {
        return
        this.scriptCache.computeIfAbsent(
            scriptOperatorType,
            (scriptName) -> new ConcurrentHashMap<>()
        );
    }

    /**
     * 通过操作类型 + 脚本名尝试从缓存中获取指定的
     * {@link DefaultRedisScript<LuaOperatorResult>}，没有则从 classpath 中加载然后缓存。
     *
     * @param operatorType  Lua 脚本类型
     * @param luaScriptName Lua 脚本名
     *
     * @return 由 {@link DefaultRedisScript} 包装的，
     *         发布 Lua 脚本执行结果 {@link LuaOperatorResult} 的 {@link Mono}
     */
    private @NotNull Mono<DefaultRedisScript<LuaOperatorResult>>
    getScriptFromCache(LuaScriptOperatorType operatorType, String luaScriptName)
    {
        return
        Mono.fromCallable(() -> {
            ConcurrentMap<String, DefaultRedisScript<LuaOperatorResult>>
                operatorCache = this.getOrCreateScriptCache(operatorType);

            // 调用 computeIfAbsent() 方法，
            // 存在则直接返回，不存在执行 mappingFunction 缓存后再返回。
            return
            operatorCache.computeIfAbsent(
                luaScriptName,
                (scriptName) -> {
                    try
                    {
                        return
                        this.loadFromClassPath(operatorType, luaScriptName);
                    }
                    catch (IOException exception)
                    {
                        throw new
                        LuaScriptOperatorFailed(
                            format(
                                "Load lua script %s failed! Caused by: %s",
                                luaScriptName, exception.getMessage()),
                            exception
                        );
                    }
                }
            );
        });
    }

    /**
     * 根据配置，读取 Lua 脚本并包装。
     *
     * @param operatorType  Lua 脚本类型
     * @param luaScriptName Lua 脚本名
     *
     * @return 由 {@link DefaultRedisScript} 包装的，
     *         发布 Lua 脚本执行结果 {@link LuaOperatorResult} 的 {@link Mono}
     */
    public @NotNull Mono<DefaultRedisScript<LuaOperatorResult>>
    read(LuaScriptOperatorType operatorType, String luaScriptName)
    {
        return
        this.getScriptFromCache(operatorType, luaScriptName)
            .subscribeOn(this.scheduler);
    }
}