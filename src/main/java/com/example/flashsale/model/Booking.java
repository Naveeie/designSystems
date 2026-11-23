package com.example.flashsale.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class Booking {

    @Id
    @GeneratedValue
    private Long id;

    private Long seatId;

    private Instant createdAt;

    protected Booking() {
    }

    public Booking(Long seatId) {
        this.seatId = seatId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSeatId() {
        return seatId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
