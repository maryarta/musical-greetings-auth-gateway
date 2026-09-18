redis.replicate_commands()

local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]

local replenish_rate = tonumber(ARGV[1])
local burst_capacity = tonumber(ARGV[2])
local requested_tokens = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])
local now = tonumber(redis.call('TIME')[1])

local last_tokens = tonumber(redis.call('get', tokens_key))
if last_tokens == nil then
    last_tokens = burst_capacity
end

local last_refreshed = tonumber(redis.call('get', timestamp_key))
if last_refreshed == nil then
    last_refreshed = 0
end

local elapsed = math.max(0, now - last_refreshed)
local filled_tokens = math.min(burst_capacity, last_tokens + elapsed * replenish_rate)
local allowed = filled_tokens >= requested_tokens
local new_tokens = filled_tokens
local retry_after = 0

if allowed then
    new_tokens = filled_tokens - requested_tokens
else
    retry_after = math.ceil((requested_tokens - filled_tokens) / replenish_rate)
end

redis.call('setex', tokens_key, ttl, new_tokens)
redis.call('setex', timestamp_key, ttl, now)

return { allowed and 1 or 0, math.floor(new_tokens), retry_after }
