# Isolation Level 테스트 프로젝트

## 개요

MySQL Isolation Level에 따른 동시성 차이를 테스트하는 프로젝트입니다.

**실무 사례 재현:**
- **문제 상황**: REPEATABLE_READ에서 동시 체크인 시 Gap Lock으로 인한 Lock wait timeout 발생
- **해결 방법**: READ_COMMITTED로 변경하여 Gap Lock 제거

---

## 테스트 환경

- Spring Boot 3.x
- MySQL 8.0 (Docker)
- `innodb_lock_wait_timeout = 1` (빠른 테스트를 위해 1초로 설정)
- Swagger UI: http://localhost:8080/swagger-ui.html

---

## 초기 데이터

앱 실행 시 자동 생성:
- **재고**: 9개 조합 (3지점 x 3객실타입) x 14일 = 126건
- **예약**: 100건 (지점/객실타입/체크인날짜 랜덤)

| 항목 | 값 |
|------|-----|
| 지점 | 서울, 부산, 제주 |
| 객실타입 | STANDARD, DELUXE, SUITE |
| 체크인 날짜 | 오늘 ~ +3일 랜덤 |

---

## API 목록

### 1. 데이터 설정

| API | 설명 |
|-----|------|
| `POST /api/setup` | 데이터 초기화 |
| `GET /api/status` | 현재 상태 조회 |

### 2. 단일 체크인

| API | Isolation Level |
|-----|-----------------|
| `POST /api/checkin/repeatable-read/{id}` | REPEATABLE_READ |
| `POST /api/checkin/read-committed/{id}` | READ_COMMITTED |

### 3. 동시 체크인 테스트 (100건)

| API | 설명                                        |
|-----|-------------------------------------------|
| `POST /api/checkin/repeatable-read/concurrent` | REPEATABLE_READ + 범위 UPDATE (Gap Lock 발생) |
| `POST /api/checkin/read-committed/concurrent` | READ_COMMITTED + 범위 UPDATE (Gap Lock 없음)  |

### 4. Isolation Level 테스트

| API | 설명 |
|-----|------|
| `POST /api/isolation-test/non-repeatable-read/repeatable-read` | Non-Repeatable Read 테스트 (REPEATABLE_READ) |
| `POST /api/isolation-test/non-repeatable-read/read-committed` | Non-Repeatable Read 테스트 (READ_COMMITTED) |

---

## 테스트 결과 예시

### 동시 체크인 (100건)

| Isolation Level                 | 성공 | 실패 | 총 시간 |
|---------------------------------|------|------|---------|
| **REPEATABLE_READ** | 77 | 23 | 12,430ms |
| **READ_COMMITTED**  | 100 | 0 | 3,439ms |

### REPEATABLE_READ (Gap Lock 발생)

```json
{
  "isolationLevel": "REPEATABLE_READ",
  "totalRequests": 100,
  "successCount": 77,
  "failCount": 23,
  "totalTimeMs": 12430
}
```
- Gap Lock으로 인해 다른 트랜잭션 블로킹
- Lock wait timeout 발생

### READ_COMMITTED (정상 처리)

```json
{
  "isolationLevel": "READ_COMMITTED",
  "totalRequests": 100,
  "successCount": 100,
  "failCount": 0,
  "totalTimeMs": 3439
}
```
- Gap Lock 없음
- 모든 요청 성공

---

## Non-Repeatable Read 테스트

### REPEATABLE_READ (값 동일 유지)

```json
{
  "isolationLevel": "REPEATABLE_READ",
  "firstSelect": 100,
  "secondSelect": 100,
  "nonRepeatableReadOccurred": false,
  "explanation": "REPEATABLE_READ는 트랜잭션 시작 시점의 snapshot을 유지하므로, 다른 트랜잭션이 UPDATE해도 같은 값을 읽습니다."
}
```

### READ_COMMITTED (값 변경됨)

```json
{
  "isolationLevel": "READ_COMMITTED",
  "firstSelect": 100,
  "secondSelect": 50,
  "nonRepeatableReadOccurred": true,
  "explanation": "READ_COMMITTED는 매번 최신 커밋된 값을 읽으므로, 다른 트랜잭션이 UPDATE/COMMIT하면 변경된 값을 읽습니다."
}
```

---

## 핵심 개념 정리

### Gap Lock이란?

REPEATABLE_READ에서 범위 쿼리 시 **존재하지 않는 row 사이의 간격(gap)까지 잠그는 것**

```sql
-- 이 쿼리가 Gap Lock을 발생시킴
UPDATE stock SET ...
WHERE date >= '2024-04-07' AND date < '2024-04-10'
```

### UPDATE 방식에 따른 차이

| 방식                     | 쿼리 | Gap Lock |
|------------------------|------|----------|
| 범위 UPDATE (range scan) | `WHERE date >= ? AND date < ?` | O |
| PK 기반 UPDATE | `WHERE branch=? AND room_type=? AND date=?` (각 row별) | X |

### 4가지 Isolation Level 비교

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | 실무 사용                    |
|-----------------|------------|---------------------|--------------|--------------------------|
| **READ_UNCOMMITTED** | O | O | O | 거의 안 씀                   |
| **READ_COMMITTED** | X | O | O | PostgreSQL 기본, Oracle 기본 |
| **REPEATABLE_READ** | X | X | X | MySQL 기본                 |
| **SERIALIZABLE** | X | X | X | 거의 안 씀 (성능 이슈)     |

---

## 참고

### Dirty Read
커밋 안 된 값을 읽음

### Non-Repeatable Read vs Phantom Read

| 현상 | 원인 | 변화 |
|------|------|------|
| Non-Repeatable Read | UPDATE/DELETE | 같은 row의 **값**이 달라짐 |
| Phantom Read | INSERT/DELETE | 범위 쿼리의 **row 수**가 달라짐 |

### JPA 1차 캐시 vs DB Snapshot

| | 1차 캐시 (JPA) | Snapshot (DB) |
|--|---------------|---------------|
| **위치** | 애플리케이션 메모리 | 데이터베이스 |
| **수명** | 트랜잭션 동안 | 트랜잭션 동안 |
| **동작** | 쿼리 자체를 안 보냄 | 쿼리는 보내지만 DB가 옛날 값 반환 |

### entityManager.clear() 후 결과가 다른 이유

```
READ_COMMITTED + clear()
────────────────────────
1. SELECT → 100 (캐시 저장)
2. (다른 트랜잭션 UPDATE → 50)
3. clear() → 캐시 삭제
4. SELECT → DB에 쿼리 → 50 반환 (최신값)

REPEATABLE_READ + clear()
────────────────────────
1. SELECT → 100 (캐시 저장, DB snapshot 생성)
2. (다른 트랜잭션 UPDATE → 50)
3. clear() → 캐시 삭제
4. SELECT → DB에 쿼리 → 100 반환 (snapshot 값)
```

- `entityManager.clear()`: JPA에게 "DB 다시 물어봐"
- **DB가 뭘 반환하느냐**는 isolation level에 따라 다름
  - READ_COMMITTED: 최신 커밋된 값
  - REPEATABLE_READ: 트랜잭션 시작 시점의 snapshot

---

## 한계

READ_COMMITTED로 Gap Lock 문제를 해결했더라도, **트래픽이 더 많아지면** 여전히 Lock wait timeout이 발생할 수 있습니다.

- Gap Lock은 제거되었지만, **Row Lock은 여전히 존재**
- 같은 row를 동시에 UPDATE하는 요청이 많아지면 대기 발생
- `innodb_lock_wait_timeout` 내에 락을 획득하지 못하면 타임아웃

### 추가 해결 방안

| 방안 | 설명 |
|------|------|
| **PK 기반 UPDATE** | 범위 쿼리 대신 각 row를 PK로 개별 UPDATE (Gap Lock 방지) |
| **DB 스케일 업** | CPU/메모리 증설로 처리량 향상 |
| **innodb_lock_wait_timeout 조정** | 대기 시간 늘리기 (단, 사용자 경험 저하) |
| **재시도 로직 (Retry)** | 타임아웃 시 일정 횟수 재시도 |
| **분산 락 (Redis)** | DB 락 의존도 낮추기 |
| **큐 기반 처리** | 동시 요청을 순차 처리로 전환 |
| **샤딩/파티셔닝** | 데이터 분산으로 락 경합 감소 |

### 결론

> Isolation Level 조정은 **동시성 문제 해결의 방식 중 하나**이며,
> 트래픽 규모에 따라 추가적인 아키텍처 개선이 필요할 수 있음
