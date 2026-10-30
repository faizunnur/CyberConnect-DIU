package edu.diu.cyberconnect.common.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis token bucket evaluated atomically in Lua. Keys are namespaced per action and subject (IP, email, user id). */
@Component
public class RateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_per_ms = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local bucket = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(bucket[1])
            local ts = tonumber(bucket[2])
            if tokens == nil then tokens = capacity; ts = now end
            local elapsed = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + elapsed * refill_per_ms)
            local allowed = 0
            if tokens >= 1 then tokens = tokens - 1; allowed = 1 end
            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, ARGV[4])
            return allowed
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(SCRIPT, Long.class);
    }

    /**
     * @param action   logical action, e.g. "login"
     * @param subject  who is limited, e.g. an IP or a normalised email
     * @param capacity burst size
     * @param period   time to refill the whole bucket
     */
    public void check(String action, String subject, int capacity, Duration period) {
        String key = "cyberconnect:ratelimit:" + action + ":" + subject;
        double refillPerMs = (double) capacity / period.toMillis();
        long ttl = Math.max(period.toMillis() * 2, 1000);
        Long allowed = redis.execute(script, List.of(key),
                String.valueOf(capacity), String.valueOf(refillPerMs),
                String.valueOf(System.currentTimeMillis()), String.valueOf(ttl));
        if (allowed == null || allowed == 0) {
            throw new RateLimitExceededException();
        }
    }
}
