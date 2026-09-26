-- Adds a player to their country's queue, at most once.
-- KEYS[1] = country queue (list), KEYS[2] = set of queued player ids
-- ARGV[1] = player id
-- Returns 1 if queued, 0 if the player was already queued.
if redis.call('SADD', KEYS[2], ARGV[1]) == 0 then
    return 0
end
redis.call('RPUSH', KEYS[1], ARGV[1])
return 1
