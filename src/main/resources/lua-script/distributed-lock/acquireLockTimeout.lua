--[[
    尝试获取一个分布式锁。

    KEYS:
        lockKeyName 分布式锁键

    ARGV:
        identifier      本锁的唯一标识符
        acquireTimeout  尝试获取锁的时间限制（毫秒级）
        lockTimeout     锁本身的持有时间限制（毫秒级）
]]

--[[
    由于脚本中出现了 TIME 这样的非确定命令，
    因此这里需要调用 redis.replicate_commands() 显式的开启单命令模式。
]]
redis.replicate_commands()

local lockKeyName = KEYS[1]

local identifier = ARGV[1]
local acquireTimeout = tonumber(ARGV[2])
local lockTimeout = tonumber(ARGV[3])

-- 分布式锁想避免 “时间漂移” 很难，
-- 但统一时间源头可以尽可能地避免这一点
local function getCurrentMillis()
    local time = redis.call('TIME')

    return tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
end

local now = getCurrentMillis()
local acquireEnd = now + acquireTimeout

-- 在指定期限内尝试获取锁
while
getCurrentMillis() < acquireEnd
do
    if
    -- 尝试设置值
    --（NX （Not Exist）选项表示只有 lockKeyName 不存在时能设置成功）
    --（PX （Expire）选项设置这个锁的有效期为多少毫秒）
    redis.call(
            'SET', lockKeyName, identifier,
            'NX', 'PX', lockTimeout
    ) ~= nil
    then
        return '{"result": "SUCCESS"}'
    end

    -- 若干设置值不成功，检查这个锁的 TTL
    local ttl = redis.call('PTTL', lockKeyName)

    if ttl == -2 then
        -- 锁不存在 NOTHING TO DO
    else if
        ttl == -1
        then
            -- 锁存在但是没有设置 TTL，补上避免永续锁
            redis.call('PEXPIRE', lockKeyName, lockTimeout)
        else if
            -- 锁存在且还有效，检查一下释放为自己的锁
            redis.call('GET', lockKeyName) == identifier
            then
                -- 如果是自己的锁，要续期
                redis.call('PEXPIRE', lockKeyName, lockTimeout)
                return '{"result": "SUCCESS"}'
            end
        end
    end

    -- 如果距离超时还差最后 100 毫秒时还没获取到锁，那可以考虑提前失败了
    if
        getCurrentMillis() + 100 >= acquireEnd
    then
        break
    end

    -- 在每一次检查循环的末尾让出执行权
    -- 避免长时间占有服务器（可能有问题？）
    -- redis.breakpoint()
end

-- 若在指定时间内没有拿到锁，则为获取锁超时
return '{"result": "GET_LOCK_TIMEOUT"}'