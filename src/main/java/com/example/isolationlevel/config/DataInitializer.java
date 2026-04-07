package com.example.isolationlevel.config;

import com.example.isolationlevel.entity.Reservation;
import com.example.isolationlevel.entity.Stock;
import com.example.isolationlevel.repository.CheckInRepository;
import com.example.isolationlevel.repository.ReservationRepository;
import com.example.isolationlevel.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final CheckInRepository checkInRepository;

    @Override
    public void run(String... args) {
        init();
        log.info("Swagger UI: http://localhost:8080/swagger-ui.html");
    }

    public void init() {
        log.info("========== 초기 데이터 설정 시작 ==========");

        // 기존 데이터 삭제
        checkInRepository.deleteAll();
        reservationRepository.deleteAll();
        stockRepository.deleteAll();

        // 지점/객실타입 조합 (3x3 = 9개 조합)
        String[] branches = {"서울", "부산", "제주"};
        String[] roomTypes = {"STANDARD", "DELUXE", "SUITE"};
        LocalDate today = LocalDate.now();

        // 재고 생성: 각 지점/객실타입 조합에 대해 14일 생성
        for (String branch : branches) {
            for (String roomType : roomTypes) {
                for (int i = 0; i < 14; i++) {
                    LocalDate date = today.plusDays(i);
                    Stock stock = new Stock(branch, roomType, date, 100);
                    stock.setReserved(100);
                    stockRepository.save(stock);
                }
            }
        }
        log.info("재고 생성: {}개 지점 x {}개 객실타입 x 14일 = {}건",
                branches.length, roomTypes.length, branches.length * roomTypes.length * 14);

        // 예약 생성: 100개
        // - 지점/객실타입 랜덤 (9개 조합)
        // - 체크인 날짜: 오늘 ~ +3일 랜덤
        // - 숙박일수: 1~3박 랜덤
        Random random = new Random(42);
        for (int i = 1; i <= 100; i++) {
            String branch = branches[random.nextInt(branches.length)];
            String roomType = roomTypes[random.nextInt(roomTypes.length)];
            int checkInOffset = random.nextInt(4);  // 0~3일 후 체크인
            int nights = random.nextInt(3) + 1;     // 1~3박

            LocalDate checkInDate = today.plusDays(checkInOffset);
            Reservation reservation = new Reservation(
                    "Guest" + i,
                    branch,
                    roomType,
                    checkInDate,
                    checkInDate.plusDays(nights)
            );
            reservationRepository.save(reservation);
        }
        log.info("예약 생성: 100건 (9개 조합 랜덤, 체크인 0~3일후, 1~3박)");

        log.info("========== 초기 데이터 설정 완료 ==========");
    }
}
