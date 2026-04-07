package com.example.isolationlevel.repository;

import com.example.isolationlevel.entity.Stock;
import com.example.isolationlevel.entity.StockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StockRepository extends JpaRepository<Stock, StockId> {

    /**
     * 날짜 범위로 재고 조회 (일반 SELECT)
     */
    @Query("SELECT s FROM Stock s WHERE s.id.branch = :branch AND s.id.roomType = :roomType " +
           "AND s.id.stockDate >= :startDate AND s.id.stockDate < :endDate ORDER BY s.id.stockDate")
    List<Stock> findByRange(
            @Param("branch") String branch,
            @Param("roomType") String roomType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 날짜 범위로 재고 UPDATE (직접 쿼리)
     * reserved -1, checkedIn +1
     */
    @Modifying
    @Query("UPDATE Stock s SET s.reserved = s.reserved - 1, s.checkedIn = s.checkedIn + 1 " +
           "WHERE s.id.branch = :branch AND s.id.roomType = :roomType " +
           "AND s.id.stockDate >= :startDate AND s.id.stockDate < :endDate")
    int updateCheckIn(
            @Param("branch") String branch,
            @Param("roomType") String roomType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * reserved 값 직접 설정 (Non-Repeatable Read 테스트용)
     */
    @Modifying
    @Query("UPDATE Stock s SET s.reserved = :newReserved " +
           "WHERE s.id.branch = :branch AND s.id.roomType = :roomType " +
           "AND s.id.stockDate >= :startDate AND s.id.stockDate < :endDate")
    int updateReserved(
            @Param("branch") String branch,
            @Param("roomType") String roomType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("newReserved") int newReserved);
}
