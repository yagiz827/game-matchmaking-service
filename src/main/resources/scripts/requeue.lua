-- Puts players back at the front of their queues, keeping their place in line.
-- Used when a popped group could not be saved as a match.
-- KEYS[i] = queue for ARGV[i]; the last key is the set of queued player ids.
local queued = KEYS[#KEYS]
local n = #KEYS - 1
local added = 0

for i = 1, n do
    if redis.call('SADD', queued, ARGV[i]) == 1 then
        redis.call('LPUSH', KEYS[i], ARGV[i])
        added = added + 1
    end
end
return added
