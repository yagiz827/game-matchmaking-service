-- Atomically takes the longest-waiting player from every country queue,
-- but only if every queue has someone. Redis runs the whole script as one
-- operation, so two matchmakers can never grab the same player.
-- KEYS[1..n-1] = one queue per country, KEYS[n] = set of queued player ids
-- Returns the player ids in KEYS order, or an empty array if any queue is empty.
-- (An empty table rather than nil: some clients decode a nil reply as [null].)
local queued = KEYS[#KEYS]
local n = #KEYS - 1

for i = 1, n do
    if redis.call('LLEN', KEYS[i]) == 0 then
        return {}
    end
end

local players = {}
for i = 1, n do
    players[i] = redis.call('LPOP', KEYS[i])
    redis.call('SREM', queued, players[i])
end
return players
