package com.example.isolationlevel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String guestName;

    private String branch;      // 지점

    private String roomType;    // 객실타입

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Reservation(String guestName, String branch, String roomType, LocalDate checkInDate, LocalDate checkOutDate) {
        this.guestName = guestName;
        this.branch = branch;
        this.roomType = roomType;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public StockId getStockId() {
        return new StockId(branch, roomType, checkInDate);
    }

    public void checkIn() {
        if (this.status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("체크인 가능한 상태가 아닙니다. 현재: " + status);
        }
        this.status = ReservationStatus.CHECKED_IN;
        this.updatedAt = LocalDateTime.now();
    }

    public enum ReservationStatus {
        RESERVED,    // 예약됨
        CHECKED_IN,  // 체크인 완료
        CHECKED_OUT, // 체크아웃 완료
        CANCELLED    // 취소됨
    }
}
