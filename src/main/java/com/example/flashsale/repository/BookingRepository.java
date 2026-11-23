package com.example.flashsale.repository;

import com.example.flashsale.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countBySeatId(Long seatId);
}
