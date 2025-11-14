--[[
    尝试释放一个分布式锁。

    KEYS:
        lockKeyName 分布式锁键

    ARGV:
        identifier 锁的唯一标识符
]]

local lockKeyName = KEYS[1]
local identifier  = ARGV[1]

-- 获取 Redis 中锁的唯一标识并比较
if
    redis.call('GET', lockKeyName) == identifier
then
    -- 如果是自己的锁直接返回
    if redis.call('DEL', lockKeyName) == 1 then
        return '{"result": "SUCCESS"}'
    else
        -- 这里有一个非常罕见的情况：
        -- 若两个客户端同时发出 releaseLock() 操作，
        -- 则两个客户端都会认为自己是锁的持有者，
        -- 在先后执行 DEL 操作时就会多出一次无意义的删除操作
        -- 无害但是值得记录
        return '{"result": "CONCURRENT_RELEASE"}'
    end
else
    -- 对于唯一标识符对不上的情况，要去 Redis 检查这个 Key 是否存在
    -- 从而判断到底是锁不存在还是碰到了别人的锁
    if
        redis.call('EXISTS', lockKeyName) == 0
    then
        return '{"result": "LOCK_NOT_EXIST"}'
    else
        return '{"result" : "LOCK_OWNED_BY_OTHERS"}'
    end
end