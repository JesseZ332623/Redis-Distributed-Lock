package io.github.jessez332623.redis_lock.utils;

import io.github.jessez332623.redis_lock.utils.exception.LuaScriptOperatorFailed;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

    /** 从 classpath 中加载脚本。*/
    @Contract("_, _ -> new")
    private @NotNull
    DefaultRedisScript<LuaOperatorResult>
    loadFromClassPath(
        @NotNull LuaScriptOperatorType operatorType,
        String luaScriptName
    ) throws IOException
    {
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

    /**
     * 根据配置，从 classpath 读取脚本并包装。
     *
     * @param operatorType  Lua 脚本类型
     * @param luaScriptName Lua 脚本名
     *
     * @return 由 {@link  DefaultRedisScript} 包装的，
     *         发布 Lua 脚本执行结果 {@link LuaOperatorResult} 的 {@link Mono}
     */
    public @NotNull Mono<DefaultRedisScript<LuaOperatorResult>>
    read(LuaScriptOperatorType operatorType, String luaScriptName)
    {
        return
        Mono.fromCallable(() ->
            this.loadFromClassPath(operatorType, luaScriptName))
        .onErrorMap(
            IOException.class,
            (exception) ->
                new LuaScriptOperatorFailed(
                    format(
                        "Load lua script %s failed! Caused by: %s",
                        luaScriptName, exception.getMessage()),
                    exception
                )
        );
    }
}