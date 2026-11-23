package com.example.flashsale.reconcile;

import com.example.flashsale.model.Seat;
import com.example.flashsale.redis.StockGate;
import com.example.flashsale.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Repairs drift between the Redis counter and Postgres. Postgres is the source of truth.
// Steps: pause new reservations, wait for in-flight ones to finish, copy Postgres into Redis, resume.
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);
    private static final long DRAIN_TIMEOUT_MS = 5_000;
    private static final long POLL_INTERVAL_MS = 10;

    private final SeatRepository seats;
    private final StockGate stockGate;

    public ReconciliationJob(SeatRepository seats, StockGate stockGate) {
        this.seats = seats;
        this.stockGate = stockGate;
    }

    // fixedDelay: the next run starts 5 seconds after this one finishes, so runs never overlap.
    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void reconcile() {
        stockGate.pause();
        try {
            if (!waitForDrain()) {
                log.warn("Reconciliation skipped: in-flight reservations did not drain within {} ms", DRAIN_TIMEOUT_MS);
                return;
            }
            // No reservation can start while paused, and none is in progress, so Postgres is settled.
            int truth = seats.findById(Seat.FLASH_SALE_ID).orElseThrow().getRemaining();
            Long before = stockGate.remaining();
            stockGate.seed(truth);
            log.info("Reconciliation: redis {} -> postgres {}", before, truth);
        } finally {
            // Runs on every outcome, so a failed or timed-out run can never leave the sale paused.
            stockGate.resume();
        }
    }

    // Returns true once no request is between enterReservation and exitReservation.
    private boolean waitForDrain() {
        long deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS;
        while (stockGate.inFlight() > 0) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
