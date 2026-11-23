package com.example.flashsale.repository;

import com.example.flashsale.model.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    // Stage 2: SELECT ... FOR UPDATE. Holds a row lock until the transaction commits.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    // Stage 3b: single conditional UPDATE. Returns rows affected: 1 = got it, 0 = sold out.
    // The version bump keeps this consistent with the optimistic strategy's version checks.
    @Modifying
    @Query("update Seat s set s.remaining = s.remaining - 1, s.version = s.version + 1 "
            + "where s.id = :id and s.remaining > 0")
    int decrementIfAvailable(@Param("id") Long id);
}
