package com.example.flashsale.seed;

import com.example.flashsale.redis.StockGate;
import com.example.flashsale.service.BookingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Resets the flash sale on every startup so each load-test run starts from the same state.
@Component
public class SeedData implements ApplicationRunner {

    private final BookingService service;
    private final StockGate stockGate;
    private final int stock;

    public SeedData(BookingService service, StockGate stockGate, @Value("${app.seed.stock}") int stock) {
        this.service = service;
        this.stockGate = stockGate;
        this.stock = stock;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Clear leftovers from a crashed run before the reset. Nothing else is running yet.
        stockGate.clearPauseState();
        service.reset(stock);
    }
}
