package com.example.flashsale.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
public class Seat {

    public static final long FLASH_SALE_ID = 1L;

    @Id
    private Long id;

    private int initialStock;

    private int remaining;

    // Stage 3a: Hibernate increments this on every UPDATE and checks it in the WHERE clause.
    @Version
    private Long version;

    protected Seat() {
    }

    public Seat(Long id, int initialStock) {
        this.id = id;
        this.initialStock = initialStock;
        this.remaining = initialStock;
    }

    public void reset(int stock) {
        this.initialStock = stock;
        this.remaining = stock;
    }

    public Long getId() {
        return id;
    }

    public int getInitialStock() {
        return initialStock;
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }
}
