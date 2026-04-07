package com.example.isolationlevel.controller;

import com.example.isolationlevel.config.DataInitializer;
import com.example.isolationlevel.entity.CheckIn;
import com.example.isolationlevel.entity.Reservation;
import com.example.isolationlevel.entity.Stock;
import com.example.isolationlevel.repository.CheckInRepository;
import com.example.isolationlevel.repository.ReservationRepository;
import com.example.isolationlevel.repository.StockRepository;
import com.example.isolationlevel.service.EntityUpdateCheckInService;
import com.example.isolationlevel.service.ReadCommittedCheckInService;
import com.example.isolationlevel.service.RepeatableReadCheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CheckInController {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final CheckInRepository checkInRepository;
    private final RepeatableReadCheckInService repeatableReadCheckInService;
    private final ReadCommittedCheckInService readCommittedCheckInService;
    private final EntityUpdateCheckInService entityUpdateCheckInService;
    private final DataInitializer dataInitializer;

    // ==================== 데이터 설정 ====================

    @Tag(name = "1. 데이터 설정")
    @Operation(summary = "테스트 데이터 초기화", description = "재고 + 예약 20건 생성")
    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setupTestData() {
        dataInitializer.init();
        return ResponseEntity.ok(Map.of("message", "테스트 데이터 초기화 완료 (재고 100개, 예약 100건)"));
    }

    @Tag(name = "1. 데이터 설정")
    @Operation(summary = "현재 상태 조회")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();

        List<Stock> stocks = stockRepository.findAll();
        result.put("stocks", stocks.stream().map(s -> Map.of(
                "branch", s.getBranch(),
                "roomType", s.getRoomType(),
                "date", s.getStockDate().toString(),
                "total", s.getTotal(),
                "reserved", s.getReserved(),
                "checkedIn", s.getCheckedIn(),
                "available", s.getAvailable()
        )).toList());

        result.put("reservations", Map.of(
                "total", reservationRepository.count(),
                "reserved", reservationRepository.findByStatus(Reservation.ReservationStatus.RESERVED).size(),
                "checkedIn", reservationRepository.findByStatus(Reservation.ReservationStatus.CHECKED_IN).size()
        ));

        result.put("checkIns", checkInRepository.count());

        return ResponseEntity.ok(result);
    }

    // ==================== 단일 체크인 ====================

    @Tag(name = "2. 단일 체크인")
    @Operation(summary = "REPEATABLE_READ 체크인")
    @PostMapping("/checkin/repeatable-read/{reservationId}")
    public ResponseEntity<Map<String, Object>> checkInRepeatableRead(@PathVariable Long reservationId) {
        try {
            CheckIn checkIn = repeatableReadCheckInService.processCheckIn(reservationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "checkInId", checkIn.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @Tag(name = "2. 단일 체크인")
    @Operation(summary = "READ_COMMITTED 체크인")
    @PostMapping("/checkin/read-committed/{reservationId}")
    public ResponseEntity<Map<String, Object>> checkInReadCommitted(@PathVariable Long reservationId) {
        try {
            CheckIn checkIn = readCommittedCheckInService.processCheckIn(reservationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "checkInId", checkIn.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 동시 체크인 테스트 ====================

    @Tag(name = "3. 동시 체크인 테스트")
    @Operation(summary = "REPEATABLE_READ 동시 체크인 - 에러 예상")
    @PostMapping("/checkin/repeatable-read/concurrent")
    public ResponseEntity<Map<String, Object>> concurrentCheckInRepeatableRead() {
        List<Long> reservationIds = getReservedIds(100);
        return executeConcurrentCheckIn(reservationIds, 120, "REPEATABLE_READ");
    }

    @Tag(name = "3. 동시 체크인 테스트")
    @Operation(summary = "READ_COMMITTED 동시 체크인 - 성공 예상")
    @PostMapping("/checkin/read-committed/concurrent")
    public ResponseEntity<Map<String, Object>> concurrentCheckInReadCommitted() {
        List<Long> reservationIds = getReservedIds(100);
        return executeConcurrentCheckIn(reservationIds, 120, "READ_COMMITTED");
    }

    @Tag(name = "3. 동시 체크인 테스트")
    @Operation(summary = "REPEATABLE_READ + Entity방식 동시 체크인 - 성공 예상")
    @PostMapping("/checkin/repeatable-read-entity/concurrent")
    public ResponseEntity<Map<String, Object>> concurrentCheckInRepeatableReadEntity() {
        List<Long> reservationIds = getReservedIds(100);
        return executeConcurrentCheckIn(reservationIds, 120, "REPEATABLE_READ_ENTITY");
    }

    private List<Long> getReservedIds(int count) {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.RESERVED)
                .stream()
                .map(Reservation::getId)
                .limit(count)
                .toList();
    }

    private ResponseEntity<Map<String, Object>> executeConcurrentCheckIn(
            List<Long> reservationIds, int timeoutSeconds, String isolationLevel) {

        log.info("========== [{}] 동시 체크인 시작 - {} 건 ==========", isolationLevel, reservationIds.size());

        ExecutorService executor = Executors.newFixedThreadPool(reservationIds.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(reservationIds.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Map<String, Object>> results = new CopyOnWriteArrayList<>();

        long startTime = System.currentTimeMillis();

        for (Long reservationId : reservationIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long threadStart = System.currentTimeMillis();
                    CheckIn checkIn;

                    if ("REPEATABLE_READ".equals(isolationLevel)) {
                        checkIn = repeatableReadCheckInService.processCheckIn(reservationId);
                    } else if ("REPEATABLE_READ_ENTITY".equals(isolationLevel)) {
                        checkIn = entityUpdateCheckInService.processCheckIn(reservationId);
                    } else {
                        checkIn = readCommittedCheckInService.processCheckIn(reservationId);
                    }

                    long elapsed = System.currentTimeMillis() - threadStart;
                    successCount.incrementAndGet();
                    results.add(Map.of(
                            "reservationId", reservationId,
                            "success", true,
                            "elapsed", elapsed + "ms"
                    ));

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    results.add(Map.of(
                            "reservationId", reservationId,
                            "success", false,
                            "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
                    ));
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        try {
            boolean completed = endLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            executor.shutdownNow();

            Map<String, Object> response = new HashMap<>();
            response.put("isolationLevel", isolationLevel);
            response.put("totalRequests", reservationIds.size());
            response.put("successCount", successCount.get());
            response.put("failCount", failCount.get());
            response.put("totalTimeMs", totalTime);
            response.put("completed", completed);
            response.put("results", results);

            log.info("========== [{}] 동시 체크인 완료 - 성공: {}, 실패: {}, 총 소요: {}ms ==========",
                    isolationLevel, successCount.get(), failCount.get(), totalTime);

            // 테스트 후 데이터 초기화
            dataInitializer.init();

            return ResponseEntity.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().body(Map.of("error", "Interrupted"));
        }
    }
}
