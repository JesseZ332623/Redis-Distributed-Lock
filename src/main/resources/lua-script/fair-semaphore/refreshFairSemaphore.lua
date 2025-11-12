--[[
    为想长期持有信号量的进程提供信号量刷新操作。

    KEYS:
        semaphoreNameKey 以时间戳为排名依据的信号量有序集合键

    ARGV:
        identifier 信号量唯一标识符（如：a7f40257-f46d-4715-8bc2-b3cef6dd5c93）
]]

local semaphoreNameKey = KEYS[1]

local identifier = ARGV[1]

local function getCurrentMillis()
    local time = redis.call('TIME')

    return tonumber(time[1]) * 1000 +
            math.floor(tonumber(time[2]) / 1000)
end

local scoreOfTimestamp = getCurrentMillis()

-- 刷新信号量时间，ZADD 使用了以下两个选项：
-- XX (Only update elements that already exist. Don't add new elements)
-- CH (Changed)
-- 组合起来的语义是：
-- 更新 semaphoreNameKey 中已经存在的 scoreOfTimestamp 的 identifier，
-- 返回更新的成员数量
local updateCount
    = redis.call(
        'ZADD', semaphoreNameKey, 'XX', 'CH',
        scoreOfTimestamp, identifier
    )

-- 检查是否成功更新
if
    updateCount ~= 1
then
     -- 若不存在直接返回错误信息
    return '{"result": "SEMAPHORE_NOT_FOUND"}'
else
    -- 反之返回成功信息
    return '{"result": "SUCCESS"}'
end