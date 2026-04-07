package com.example.isolationlevel.service;

import com.example.isolationlevel.entity.Stock;
import com.example.isolationlevel.repository.StockRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
@RequiredArgsConstructor
public class NonRepeatableReadTestService {

    private final StockRepository stockRepository;
    private final EntityManager entityManager;

    /**
     * REPEATABLE_READ에서 테스트
     * - 첫 번째 SELECT 후 다른 트랜잭션이 UPDATE해도 두 번째 SELECT 결과 동일
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> testRepeatableRead(CountDownLatch firstSelectDone, CountDownLatch updateDone) {
        return doTest("REPEATABLE_READ", firstSelectDone, updateDone);
    }

    /**
     * READ_COMMITTED에서 테스트
     * - 첫 번째 SELECT 후 다른 트랜잭션이 UPDATE하면 두 번째 SELECT 결과 변경됨
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> testReadCommitted(CountDownLatch firstSelectDone, CountDownLatch updateDone) {
        return doTest("READ_COMMITTED", firstSelectDone, updateDone);
    }

    private Map<String, Object> doTest(String isolationLevel, CountDownLatch firstSelectDone, CountDownLatch updateDone) {
        Map<String, Object> result = new HashMap<>();
        result.put("isolationLevel", isolationLevel);

        String branch = "서울";
        String roomType = "STANDARD";
        LocalDate today = LocalDate.now();

        // 1. 첫 번째 SELECT
        log.info("[{}] 1. 첫 번째 SELECT 실행", isolationLevel);
        List<Stock> firstSelect = stockRepository.findByRange(branch, roomType, today, today.plusDays(1));
        int firstReserved = firstSelect.isEmpty() ? 0 : firstSelect.get(0).getReserved();
        log.info("[{}] 첫 번째 SELECT 결과: reserved = {}", isolationLevel, firstReserved);
        result.put("firstSelect", firstReserved);

        // 2. 다른 트랜잭션이 UPDATE할 수 있도록 신호
        firstSelectDone.countDown();

        // 3. 다른 트랜잭션의 UPDATE 완료 대기
        try {
            log.info("[{}] 다른 트랜잭션의 UPDATE 대기 중...", isolationLevel);
            updateDone.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. 두 번째 SELECT (같은 트랜잭션 내)
        // JPA 1차 캐시 클리어 → DB에서 새로 읽어오도록 강제
        entityManager.clear();
        log.info("[{}] 2. 두 번째 SELECT 실행 (1차 캐시 클리어 후)", isolationLevel);
        List<Stock> secondSelect = stockRepository.findByRange(branch, roomType, today, today.plusDays(1));
        int secondReserved = secondSelect.isEmpty() ? 0 : secondSelect.get(0).getReserved();
        log.info("[{}] 두 번째 SELECT 결과: reserved = {}", isolationLevel, secondReserved);
        result.put("secondSelect", secondReserved);

        // 5. 결과 분석
        boolean isNonRepeatableRead = (firstReserved != secondReserved);
        result.put("nonRepeatableReadOccurred", isNonRepeatableRead);

        if (isNonRepeatableRead) {
            log.info("[{}] Non-Repeatable Read 발생! {} → {}", isolationLevel, firstReserved, secondReserved);
        } else {
            log.info("[{}] Non-Repeatable Read 안 발생 (값 동일: {})", isolationLevel, firstReserved);
        }

        return result;
    }

    /**
     * 별도 트랜잭션에서 UPDATE 실행 (트랜잭션 B 역할)
     * 직접 UPDATE 쿼리 사용 → 즉시 반영
     */
    @Transactional
    public void updateStockInSeparateTransaction(int newReserved) {
        String branch = "서울";
        String roomType = "STANDARD";
        LocalDate today = LocalDate.now();

        log.info("[트랜잭션 B] UPDATE 실행: reserved = {}", newReserved);
        int updated = stockRepository.updateReserved(branch, roomType, today, today.plusDays(1), newReserved);
        log.info("[트랜잭션 B] UPDATE 완료 - {}건, COMMIT 예정", updated);
    }
}
