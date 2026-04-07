package com.example.isolationlevel.service;

import com.example.isolationlevel.entity.CheckIn;
import com.example.isolationlevel.entity.Reservation;
import com.example.isolationlevel.entity.Stock;
import com.example.isolationlevel.repository.CheckInRepository;
import com.example.isolationlevel.repository.ReservationRepository;
import com.example.isolationlevel.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 체크인 공통 로직 (트랜잭션 없음 - 호출하는 쪽에서 트랜잭션 관리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInProcessor {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final CheckInRepository checkInRepository;

    public CheckIn process(Long reservationId, String isolationLevel) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        log.info("[{}][{}] 체크인 시작 - 예약ID: {}", isolationLevel, threadName, reservationId);

        // 1. 예약 조회
        log.info("[{}][{}] 1. 예약 조회", isolationLevel, threadName);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        // 2. 재고 조회 (날짜 범위) - 일반 SELECT (UPDATE 시점에 락 발생)
        log.info("[{}][{}] 2. 재고 조회 (범위) - {}/{}/{} ~ {}",
                isolationLevel, threadName,
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

        log.info("[{}][{}] {}일치 재고 조회됨", isolationLevel, threadName, stocks.size());

        // 3. 재고 UPDATE (범위 쿼리)
        log.info("[{}][{}] 3. 재고 UPDATE 실행", isolationLevel, threadName);
        int updatedCount = stockRepository.updateCheckIn(
                reservation.getBranch(),
                reservation.getRoomType(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate()
        );
        log.info("[{}][{}] 재고 업데이트 완료 - {}건", isolationLevel, threadName, updatedCount);

        // 4. 다른 비즈니스 로직 처리 시뮬레이션 (UPDATE 후 lock 유지 상태)
        log.info("[{}][{}] 4. 다른 비즈니스 로직 처리 중...", isolationLevel, threadName);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 5. 예약 상태 업데이트
        log.info("[{}][{}] 5. 예약 상태 업데이트", isolationLevel, threadName);
        reservation.checkIn();
        reservationRepository.saveAndFlush(reservation);

        // 6. 체크인 데이터 생성
        log.info("[{}][{}] 6. 체크인 데이터 생성", isolationLevel, threadName);
        CheckIn checkIn = new CheckIn(reservation);
        checkInRepository.save(checkIn);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[{}][{}] 체크인 완료 - 소요시간: {}ms, 숙박일수: {}",
                isolationLevel, threadName, elapsed, stocks.size());

        return checkIn;
    }
}
