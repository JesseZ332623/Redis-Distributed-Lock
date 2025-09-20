--[[
    尝试获取一个分布式锁。

    KEYS:
        lockKeyName 分布式锁键

    ARGV:
        identifier      本锁的唯一标识符
        acquireTimeout  尝试获取锁的时间限制
        lockTimeout     锁本身的持有时间限制
]]

--[[
    由于脚本中出现了 TIME 这样的非确定命令，
    因此这里需要调用 redis.replicate_commands() 显式的开启单命令模式，
    以约 30% 的性能下降换绝对的一致。
]]
redis.replicate_commands()

local lockKeyName = KEYS[1]

local identifier     = ARGV[1]
local acquireTimeout = tonumber(ARGV[2])
local lockTimeout    = tonumber(ARGV[3])

-- 分布式锁想避免 “时间漂移” 很难，
-- 但统一时间源头可以尽可能地避免这一点
local now        = tonumber(redis.call('TIME')[1])
local acquireEnd = now + acquireTimeout

-- 在指定期限内尝试获取锁
while
    tonumber(redis.call('TIME')[1]) < acquireEnd
do
    if
        -- 尝试设置值
        --（NX （Not Exist）选项表示只有 lockKeyName 不存在时能设置成功）
        --（EX （Expire）选项设置这个数据的有效期）
        redis.call(
            'SET', lockKeyName, identifier,
            'NX', 'EX', lockTimeout
        ) ~= nil
    then
        return '{"result": "SUCCESS"}'
    elseif
        -- 若这个锁存在，但是未设置有效期
        redis.call('TTL', lockKeyName) == -1
    then
        if
            -- 最好还是验一下锁的唯一标识（是不是自己的锁？）
            redis.call('GET', lockKeyName) == identifier
        then
            -- 续上这个锁的有效期
            redis.call('EXPIRE', lockKeyName, lockTimeout)
        end
    end

    -- 如果距离超时还差最后 0.1 秒时还没获取到锁，那可以考虑提前失败了
    if
        tonumber(redis.call('TIME')[1]) + 0.1 >= acquireEnd
    then
        break
    end

    -- 在每一次检查循环的末尾让出执行权
    -- 避免长时间占有服务器
    redis.breakpoint()
end

-- 若在指定时间内没有拿到锁，则为获取锁超时
return '{"result": "GET_LOCK_TIMEOUT"}'