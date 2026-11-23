# Flash-sale lab

A deliberately naive flash-sale backend, used to learn locking, Redis, Kafka, and queues by measuring failures.

## Run

```bash
docker compose up -d          # Postgres (Redis and Kafka are for later stages)
mvn spring-boot:run
```

Start the app with the strategy you want to test:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--booking.strategy=naive
```

Startup resets stock to 100 and clears bookings, so every run starts clean.

## Load test

```bash
k6 run k6/buy.js
curl localhost:8080/stats
```

`/stats` returns:
- `remaining`: the stock counter
- `bookings`: rows in the bookings table
- `oversold`: bookings beyond initial stock. Anything above 0 is the race condition.
- `consistent`: false if the counter disagrees with the bookings table
- `redisRemaining`: the Redis counter (null if the key is missing)
- `redisInSync`: true if Redis matches Postgres `remaining`. Only the `redis` strategy keeps it current, so it's expected to be false after other strategies.

To run the next strategy, restart the app with `booking.strategy` set to `pessimistic` or `atomic`.

## Strategies

| Value | Stage | What it does |
|---|---|---|
| `naive` | 1 | read, check, write. No lock. |
| `pessimistic` | 2 | `SELECT ... FOR UPDATE` |
| `atomic` | 3b | conditional `UPDATE ... WHERE remaining > 0` |
| `optimistic` | 3a | read without lock, check `version` at commit, retry on conflict |

Note: `Seat` now has a `@Version` column, so every strategy's writes check the version. Without a lock, `naive` now fails with a conflict error instead of overselling.

Keep the Hikari pool size (20) and the k6 settings fixed across runs, or the numbers aren't comparable.
