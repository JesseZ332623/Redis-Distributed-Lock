package io.github.jessez332623.redis_lock.utils.exception;

/** 在指定文件目录下找不到指定 Lua 脚本时抛出本异常。*/
public class LuaScriptOperatorFailed extends RuntimeException
{
    public LuaScriptOperatorFailed(String message) {
        super(message);
    }

    public LuaScriptOperatorFailed(String message, Throwable throwable) {
        super(message, throwable);
    }
}