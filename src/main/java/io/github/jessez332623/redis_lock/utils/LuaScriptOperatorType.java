package io.github.jessez332623.redis_lock.utils;

import lombok.Getter;

/** 要读取的 Lua 脚本的类型枚举。*/
public enum LuaScriptOperatorType
{
    DISTRIBUTE_LOCK("distributed-lock"),
    FAIR_SEMAPHORE("fair-semaphore");

    @Getter
    final String typeName;

    LuaScriptOperatorType(String tpn) {
        this.typeName = tpn;
    }
}