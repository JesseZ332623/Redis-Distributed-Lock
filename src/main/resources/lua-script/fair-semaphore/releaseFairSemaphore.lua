--[[
    尝试释放一个信号量。
]]
local semaphoreNameKey  = KEYS[1]
local semaphoreOwnerKey = KEYS[2]

local identifier = ARGV[1]

local isRemoved = redis.call('ZREM', semaphoreNameKey, identifier)
redis.call('ZREM', semaphoreOwnerKey, identifier)

-- 检查是否成功移除
if
    isRemoved == 1
then
    return '{"result": "SUCCESS"}'
else
    -- 若移除失败，
    -- 说明信号量因业务逻辑执行超时而被别的
    -- acquireFairSemaphore() 操作删除
    return '{"result": "SEMAPHORE_TIMEOUT"}'
end