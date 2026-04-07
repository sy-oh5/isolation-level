package com.example.isolationlevel.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Stock {

    @EmbeddedId
    private StockId id;

    private int total;          // 전체 객실 수

    private int reserved;       // 예약된 수

    private int checkedIn;      // 체크인된 수

    public Stock(String branch, String roomType, LocalDate stockDate, int total) {
        this.id = new StockId(branch, roomType, stockDate);
        this.total = total;
        this.reserved = 0;
        this.checkedIn = 0;
    }

    public String getBranch() {
        return id.getBranch();
    }

    public String getRoomType() {
        return id.getRoomType();
    }

    public LocalDate getStockDate() {
        return id.getStockDate();
    }

    public int getAvailable() {
        return total - reserved - checkedIn;
    }

    public void increaseReserved() {
        if (getAvailable() <= 0) {
            throw new IllegalStateException("예약 가능한 객실이 없습니다.");
        }
        this.reserved++;
    }

    public void checkIn() {
        if (this.reserved <= 0) {
            throw new IllegalStateException("체크인할 예약이 없습니다.");
        }
        this.reserved--;
        this.checkedIn++;
    }
}
