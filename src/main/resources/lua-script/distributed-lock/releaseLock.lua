--[[
    尝试释放一个分布式锁。

    KEYS:
        lockKeyName 分布式锁键

    ARGV:
        identifier 锁的唯一标识符
]]

local lockKeyName = KEYS[1]

local identifier  = ARGV[1]

-- 获取 Redis 中锁的唯一标识
local currentIdentifier
    = redis.call('GET', lockKeyName)

-- 验证一下本锁是否存在（虽然不存在的概率很小）
if not
    currentIdentifier
then
    return '{"result": "LOCK_NOT_EXIST"}'
end

-- 比对一下是不是自己的锁
if
    currentIdentifier == identifier
then
    --  删除锁
    local delRes = redis.call("DEL", lockKeyName)

    if
        delRes == 1
    then
        return '{"result": "SUCCESS"}'
    else
        -- 这里有一个非常罕见的情况：
        -- 若两个客户端同时发出 releaseLock() 操作，
        -- 则两个客户端都会认为自己是锁的持有者，
        -- 在先后执行 DEL 操作时就会多出一次无意义的删除操作
        -- 无害但是值得记录
        return '{"result": "CONCURRENT_DELETE"}'
    end
end

-- 如果是别人的锁，直接返回
return '{"result" : "LOCK_OWNED_BY_OTHERS"}'