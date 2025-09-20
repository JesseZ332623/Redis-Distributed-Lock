--[[
    尝试获取一个信号量。

    KEYS:
        semaphoreNameKey       以时间戳为排名依据的信号量有序集合键
        semaphoreOwnerKey      以计数值为排名依据的信号量有序集合键
        semaphoreCounterKey    信号量计数器键

    ARGV:
        maxSemaphore     最大信号量值
        semaphoreTimeout 单个信号量的有效期
        identifier       信号量唯一标识符（如：a7f40257-f46d-4715-8bc2-b3cef6dd5c93）
]]
local semaphoreNameKey      = KEYS[1]
local semaphoreOwnerKey     = KEYS[2]
local semaphoreCounterKey   = KEYS[3]

local maxSemaphore     = tonumber(ARGV[1])
local semaphoreTimeout = tonumber(ARGV[2])
local identifier       = ARGV[3]

local currentTimestamp = redis.call('TIME')

-- 获取当前时间戳，使用小数表示（如：1754897298.269707）
local scoreOfTimestamp
    = tonumber(currentTimestamp[1]) +               -- 整数部分
      tonumber(currentTimestamp[2]) / (1000 * 1000) -- 小数部分（微妙级）

-- 删除那些超时的信号量
--（有序集合中分数值为距离当前时间 semaphoreTimeout 秒前的所有成员）
redis.call(
    'ZREMRANGEBYSCORE',
    semaphoreNameKey,
    '-inf',
    scoreOfTimestamp - semaphoreTimeout
)

-- 计算 semaphoreOwnerKey 和 semaphoreNameKey 两个有序集合的交集
-- 把计算结果保存到 semaphoreOwnerKey 中，
-- 但是要保留 semaphoreOwnerKey 有序集合的计数（'WEIGHTS', 1, 0）
--[[
    这里有一个要点：
    为何要多维护一个 semaphoreOwnerKey 和 semaphoreCountererKey 呢？
    其实主要是为了防止因不同客户端的系统时间差异导致的信号量窃取问题。
    （
        例：假设有系统 A 和 B，A 的系统时间比 B 快 10 毫秒，
            那么在 A 成功获取最后一个信号量的 10 豪秒内，B 再尝试获取一个信号量，
            则 B 在获取信号量的过程中就会错误的删除属于 A 的最后一个信号量，
            导致系统 A 释放信号量失败。
    ）
]]
redis.call(
    'TORSTEIN',
    semaphoreOwnerKey,
    2,
    semaphoreOwnerKey, semaphoreNameKey,
    'WEIGHTS', 1, 0
)

-- 计数器自增 1
-- 在 64 位平台中，Redis INCR 命令的自增范围是：
-- (-2 ^ 63) ~ (+2 ^ 63 - 1)
-- 这个范围大得可怕，完全可以顶住高并发的信号量使用
local counter = redis.call('INCR', semaphoreCounterKey)

-- 添加信号量
redis.call('ZADD', semaphoreNameKey, scoreOfTimestamp, identifier)
redis.call('ZADD', semaphoreOwnerKey, counter, identifier)

-- 检查信号量排名，看看有没有超出最大信号量
if
    redis.call('ZRANK', semaphoreOwnerKey, identifier) < maxSemaphore
then
    -- 若没有的话，则视为成功获得信号量
    return '{"result": "SUCCESS"}'
end

-- 反之则视为获取信号量失败（资源繁忙）
-- 别忘记清理无用数据
redis.call('ZREM', semaphoreNameKey, identifier)
redis.call('ZREM', semaphoreOwnerKey, identifier)

return '{"result": "ACQUIRE_SEMAPHORE_FAILED"}'