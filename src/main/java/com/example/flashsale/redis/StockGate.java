package com.example.flashsale.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.IntSupplier;

// Keeps the stock counter in Redis. Requests check here before touching Postgres.
@Component
public class StockGate {

    private static final String KEY = "flashsale:seat:1:remaining";
    private static final String INFLIGHT_KEY = "flashsale:seat:1:inflight";
    private static final String PAUSED_KEY = "flashsale:seat:1:paused";
    private static final Duration PAUSE_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public StockGate(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // Sets the counter to the starting stock. Called on reset.
    public void seed(int stock) {
        redis.opsForValue().set(KEY, String.valueOf(stock));
    }

    // DECR is atomic in Redis, so two callers can never both take the last unit.
    // If the counter went below zero, undo our decrement and report sold out.
    // If the counter is missing (for example after a Redis restart), rebuild it from the
    // database first. Without this, a missing key would look like sold out.
    public boolean tryReserve(IntSupplier stockInDatabase) {
        if (!redis.hasKey(KEY)) {
            // setIfAbsent writes only if the key is still missing, so concurrent rebuilds can't overwrite each other.
            redis.opsForValue().setIfAbsent(KEY, String.valueOf(stockInDatabase.getAsInt()));
        }
        Long left = redis.opsForValue().decrement(KEY);
        if (left != null && left >= 0) {
            return true;
        }
        redis.opsForValue().increment(KEY);
        return false;
    }

    // Called before a reservation starts. Counts the request as in flight first, then checks the
    // pause flag. Reconciliation sets the flag first, then waits for the in-flight count to reach 0.
    // Whichever runs first, the other sees it. Returns false if reservations are paused.
    public boolean enterReservation() {
        redis.opsForValue().increment(INFLIGHT_KEY);
        if (Boolean.TRUE.equals(redis.hasKey(PAUSED_KEY))) {
            redis.opsForValue().decrement(INFLIGHT_KEY);
            return false;
        }
        return true;
    }

    // Called when a reservation ends, whatever the outcome. Must run in a finally block.
    public void exitReservation() {
        redis.opsForValue().decrement(INFLIGHT_KEY);
    }

    // The pause expires on its own after PAUSE_TTL. If the job crashes before resume(), sales
    // come back without a manual fix. The TTL must be longer than a normal run (drain wait plus copy).
    public void pause() {
        redis.opsForValue().set(PAUSED_KEY, "1", PAUSE_TTL);
    }

    public void resume() {
        redis.delete(PAUSED_KEY);
    }

    // Only for startup, before any request runs. A crash can leave these keys behind,
    // so a stale pause would block sales and a stale in-flight count would never reach 0.
    public void clearPauseState() {
        redis.delete(List.of(PAUSED_KEY, INFLIGHT_KEY));
    }

    public long inFlight() {
        String value = redis.opsForValue().get(INFLIGHT_KEY);
        return value == null ? 0 : Long.parseLong(value);
    }

    // Read-only snapshot for /stats. Returns null when the key is missing.
    public Long remaining() {
        String value = redis.opsForValue().get(KEY);
        return value == null ? null : Long.parseLong(value);
    }

    // Gives a unit back. Used when the Postgres write fails after Redis already reserved it.
    public void release() {
        redis.opsForValue().increment(KEY);
    }
}
