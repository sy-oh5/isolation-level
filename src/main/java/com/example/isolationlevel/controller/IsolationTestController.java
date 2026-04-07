package com.example.isolationlevel.controller;

import com.example.isolationlevel.config.DataInitializer;
import com.example.isolationlevel.service.NonRepeatableReadTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/isolation-test")
@Tag(name = "4. Isolation Level 테스트")
public class IsolationTestController {

    private final NonRepeatableReadTestService testService;
    private final DataInitializer dataInitializer;

    @Operation(summary = "Non-Repeatable Read 테스트 (REPEATABLE_READ)",
            description = "첫 번째 SELECT → 다른 트랜잭션 UPDATE → 두 번째 SELECT (값 동일해야 함)")
    @PostMapping("/non-repeatable-read/repeatable-read")
    public ResponseEntity<Map<String, Object>> testRepeatableRead() {
        return executeTest("REPEATABLE_READ");
    }

    @Operation(summary = "Non-Repeatable Read 테스트 (READ_COMMITTED)",
            description = "첫 번째 SELECT → 다른 트랜잭션 UPDATE → 두 번째 SELECT (값 변경됨)")
    @PostMapping("/non-repeatable-read/read-committed")
    public ResponseEntity<Map<String, Object>> testReadCommitted() {
        return executeTest("READ_COMMITTED");
    }

    private ResponseEntity<Map<String, Object>> executeTest(String isolationLevel) {
        log.info("========== Non-Repeatable Read 테스트 시작 [{}] ==========", isolationLevel);

        CountDownLatch firstSelectDone = new CountDownLatch(1);
        CountDownLatch updateDone = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<String, Object> response = new HashMap<>();

        try {
            // 트랜잭션 A: 메인 테스트 (첫 번째 SELECT → 대기 → 두 번째 SELECT)
            Future<Map<String, Object>> transactionA = executor.submit(() -> {
                if ("REPEATABLE_READ".equals(isolationLevel)) {
                    return testService.testRepeatableRead(firstSelectDone, updateDone);
                } else {
                    return testService.testReadCommitted(firstSelectDone, updateDone);
                }
            });

            // 트랜잭션 B: UPDATE (트랜잭션 A의 첫 번째 SELECT 완료 후 실행)
            Future<?> transactionB = executor.submit(() -> {
                try {
                    // 트랜잭션 A의 첫 번째 SELECT 완료 대기
                    firstSelectDone.await();
                    Thread.sleep(100); // 약간의 딜레이

                    // UPDATE 실행 (새로운 값으로)
                    testService.updateStockInSeparateTransaction(50);

                    // UPDATE 완료 신호
                    updateDone.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 결과 수집
            Map<String, Object> result = transactionA.get();
            transactionB.get();

            response.putAll(result);
            response.put("explanation", getExplanation(isolationLevel, result));

            log.info("========== Non-Repeatable Read 테스트 완료 [{}] ==========", isolationLevel);

            // 테스트 후 데이터 초기화
            dataInitializer.init();

        } catch (Exception e) {
            response.put("error", e.getMessage());
        } finally {
            executor.shutdown();
        }

        return ResponseEntity.ok(response);
    }

    private String getExplanation(String isolationLevel, Map<String, Object> result) {
        boolean occurred = (boolean) result.getOrDefault("nonRepeatableReadOccurred", false);

        if ("REPEATABLE_READ".equals(isolationLevel)) {
            if (!occurred) {
                return "REPEATABLE_READ는 트랜잭션 시작 시점의 snapshot을 유지하므로, " +
                        "다른 트랜잭션이 UPDATE해도 같은 값을 읽습니다. (Non-Repeatable Read 방지)";
            } else {
                return "예상과 다르게 Non-Repeatable Read가 발생했습니다.";
            }
        } else {
            if (occurred) {
                return "READ_COMMITTED는 매번 최신 커밋된 값을 읽으므로, " +
                        "다른 트랜잭션이 UPDATE/COMMIT하면 변경된 값을 읽습니다. (Non-Repeatable Read 발생)";
            } else {
                return "UPDATE가 반영되지 않았거나 타이밍 이슈일 수 있습니다.";
            }
        }
    }
}
