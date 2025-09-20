package io.github.jessez332623.redis_lock.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lua 脚本的执行结果是一个 JSON 类型，会被映射成该 POJO。*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LuaOperatorResult
{
    private String result;
}