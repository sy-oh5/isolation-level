package com.example.isolationlevel.service;

import com.example.isolationlevel.entity.CheckIn;
import com.example.isolationlevel.entity.Reservation;
import com.example.isolationlevel.entity.Stock;
import com.example.isolationlevel.repository.CheckInRepository;
import com.example.isolationlevel.repository.ReservationRepository;
import com.example.isolationlevel.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * REPEATABLE_READ + Entity 방식 UPDATE
 * - 범위 쿼리 대신 PK 기준 개별 UPDATE → gap lock 없음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityUpdateCheckInService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final CheckInRepository checkInRepository;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CheckIn processCheckIn(Long reservationId) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        String label = "REPEATABLE_READ(Entity)";

        log.info("[{}][{}] 체크인 시작 - 예약ID: {}", label, threadName, reservationId);

        // 1. 예약 조회
        log.info("[{}][{}] 1. 예약 조회", label, threadName);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        // 2. 재고 조회 (날짜 범위)
        log.info("[{}][{}] 2. 재고 조회 (범위) - {}/{}/{} ~ {}",
                label, threadName,
                reservation.getBranch(), reservation.getRoomType(),
                reservation.getCheckInDate(), reservation.getCheckOutDate());

        List<Stock> stocks = stockRepository.findByRange(
                reservation.getBranch(),
                reservation.getRoomType(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate()
        );

        if (stocks.isEmpty()) {
            throw new IllegalStateException("재고 정보를 찾을 수 없습니다.");
        }

        log.info("[{}][{}] {}일치 재고 조회됨", label, threadName, stocks.size());

        // 3. 재고 UPDATE (Entity 방식 - PK 기준 개별 UPDATE)
        log.info("[{}][{}] 3. 재고 UPDATE 실행 (Entity 방식)", label, threadName);
        for (Stock stock : stocks) {
            stock.setReserved(stock.getReserved() - 1);
            stock.setCheckedIn(stock.getCheckedIn() + 1);
        }
        stockRepository.flush();  // 강제 flush → 개별 UPDATE 쿼리 실행
        log.info("[{}][{}] 재고 업데이트 완료 - {}건", label, threadName, stocks.size());

        // 4. 다른 비즈니스 로직 처리 시뮬레이션 (UPDATE 후 lock 유지 상태)
        log.info("[{}][{}] 4. 다른 비즈니스 로직 처리 중...", label, threadName);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 5. 예약 상태 업데이트
        log.info("[{}][{}] 5. 예약 상태 업데이트", label, threadName);
        reservation.checkIn();
        reservationRepository.saveAndFlush(reservation);

        // 6. 체크인 데이터 생성
        log.info("[{}][{}] 6. 체크인 데이터 생성", label, threadName);
        CheckIn checkIn = new CheckIn(reservation);
        checkInRepository.save(checkIn);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[{}][{}] 체크인 완료 - 소요시간: {}ms, 숙박일수: {}",
                label, threadName, elapsed, stocks.size());

        return checkIn;
    }
}
