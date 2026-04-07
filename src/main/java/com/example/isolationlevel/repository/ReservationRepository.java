package com.example.isolationlevel.repository;

import com.example.isolationlevel.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByStatus(Reservation.ReservationStatus status);
}
