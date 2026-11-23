package com.example.flashsale.service;

import com.example.flashsale.model.Booking;
import com.example.flashsale.model.Seat;
import com.example.flashsale.redis.StockGate;
import com.example.flashsale.repository.BookingRepository;
import com.example.flashsale.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.BooleanSupplier;

@Service
public class BookingService {

    private static final int MAX_OPTIMISTIC_ATTEMPTS = 20;

    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final TransactionTemplate transactions;
    private final StockGate stockGate;
    private final String strategy;

    public BookingService(SeatRepository seats,
                          BookingRepository bookings,
                          StockGate stockGate,
                          PlatformTransactionManager transactionManager,
                          @Value("${booking.strategy}") String strategy) {
        this.seats = seats;
        this.bookings = bookings;
        this.stockGate = stockGate;
        this.transactions = new TransactionTemplate(transactionManager);
        this.strategy = strategy;
    }

    // No @Transactional here. Each attempt needs its own transaction, so each strategy
    // opens one itself. The optimistic retry loop runs outside any transaction.
    public boolean book() {
        return switch (strategy) {
            case "naive" -> inTransaction(this::bookNaive);
            case "pessimistic" -> inTransaction(this::bookPessimistic);
            case "atomic" -> inTransaction(this::bookAtomic);
            case "optimistic" -> bookOptimisticWithRetry();
            case "redis" -> bookWithRedis();
            default -> throw new IllegalStateException("Unknown booking.strategy: " + strategy);
        };
    }

    private boolean inTransaction(BooleanSupplier work) {
        return transactions.execute(status -> work.getAsBoolean());
    }

    // Stage 1: read, check, write. Under READ COMMITTED, two transactions can both
    // read remaining = 1 and both decrement it. This is the race condition.
    private boolean bookNaive() {
        Seat seat = seats.findById(Seat.FLASH_SALE_ID).orElseThrow();
        if (seat.getRemaining() <= 0) {
            return false;
        }
        seat.setRemaining(seat.getRemaining() - 1);
        bookings.save(new Booking(Seat.FLASH_SALE_ID));
        return true;
    }

    // Stage 2: the row lock makes concurrent transactions wait for each other.
    private boolean bookPessimistic() {
        Seat seat = seats.findByIdForUpdate(Seat.FLASH_SALE_ID).orElseThrow();
        if (seat.getRemaining() <= 0) {
            return false;
        }
        seat.setRemaining(seat.getRemaining() - 1);
        bookings.save(new Booking(Seat.FLASH_SALE_ID));
        return true;
    }

    // Stage 3a: read without a lock. The UPDATE at commit includes "WHERE version = <value I read>".
    // If another request changed the row in between, that UPDATE matches 0 rows and Hibernate
    // throws instead of overwriting. The retry loop below handles that case.
    private boolean bookOptimistic() {
        Seat seat = seats.findById(Seat.FLASH_SALE_ID).orElseThrow();
        if (seat.getRemaining() <= 0) {
            return false;
        }
        seat.setRemaining(seat.getRemaining() - 1);
        bookings.save(new Booking(Seat.FLASH_SALE_ID));
        return true;
    }

    private boolean bookOptimisticWithRetry() {
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            try {
                return inTransaction(this::bookOptimistic);
            } catch (OptimisticLockingFailureException conflict) {
                // The failed transaction is already rolled back. Loop around and read the row again.
            }
        }
        throw new IllegalStateException("Still conflicting after " + MAX_OPTIMISTIC_ATTEMPTS + " attempts");
    }

    // Stage 3b: no read-then-write window. The database decides in one statement.
    private boolean bookAtomic() {
        if (seats.decrementIfAvailable(Seat.FLASH_SALE_ID) == 0) {
            return false;
        }
        bookings.save(new Booking(Seat.FLASH_SALE_ID));
        return true;
    }

    // Stage 4: Redis reserves first. Only a successful reservation reaches Postgres.
    // If Postgres refuses or throws, the reservation is released back to Redis.
    private boolean bookWithRedis() {
        // Registers this request as in flight. Refused while reconciliation is pausing reservations.
        if (!stockGate.enterReservation()) {
            throw new SalePausedException();
        }
        try {
            if (!stockGate.tryReserve(() -> seats.findById(Seat.FLASH_SALE_ID).orElseThrow().getRemaining())) {
                return false;
            }
            // Release only when we know Postgres wrote nothing: a sold-out answer means no insert.
            // If Postgres throws, the commit may or may not have happened, so the unit stays reserved.
            // A leaked unit under-sells, which reconciliation can repair. A wrong release can oversell.
            boolean booked = inTransaction(this::writeBookingAfterReserve);
            if (!booked) {
                stockGate.release();
            }
            return booked;
        } finally {
            // Always runs, so the in-flight count returns to its correct value on every outcome.
            stockGate.exitReservation();
        }
    }

    // Postgres side of the redis strategy. Only runs after Redis has reserved a unit.
    private boolean writeBookingAfterReserve() {
        if (seats.decrementIfAvailable(Seat.FLASH_SALE_ID) == 0) {
            return false;
        }
        bookings.save(new Booking(Seat.FLASH_SALE_ID));
        return true;
    }

    @Transactional
    public void reset(int stock) {
        bookings.deleteAll();
        seats.findById(Seat.FLASH_SALE_ID)
                .ifPresentOrElse(seat -> seat.reset(stock),
                        () -> seats.save(new Seat(Seat.FLASH_SALE_ID, stock)));
        stockGate.seed(stock);
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        Seat seat = seats.findById(Seat.FLASH_SALE_ID).orElseThrow();
        long sold = bookings.countBySeatId(Seat.FLASH_SALE_ID);
        long oversold = Math.max(0, sold - seat.getInitialStock());
        // If remaining doesn't equal initialStock - sold, the counter and the bookings disagree.
        boolean consistent = seat.getRemaining() == seat.getInitialStock() - sold;
        // Redis is only kept current by the redis strategy. Other strategies leave it stale.
        Long redisRemaining = stockGate.remaining();
        boolean redisInSync = redisRemaining != null && redisRemaining == seat.getRemaining();
        return new Stats(seat.getInitialStock(), seat.getRemaining(), sold, oversold, consistent,
                redisRemaining, redisInSync);
    }

    public record Stats(int initialStock, int remaining, long bookings, long oversold, boolean consistent,
                        Long redisRemaining, boolean redisInSync) {
    }
}
