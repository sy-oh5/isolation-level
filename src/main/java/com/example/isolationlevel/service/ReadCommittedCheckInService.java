package com.example.isolationlevel.service;

import com.example.isolationlevel.entity.CheckIn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadCommittedCheckInService {

    private final CheckInProcessor checkInProcessor;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckIn processCheckIn(Long reservationId) {
        return checkInProcessor.process(reservationId, "READ_COMMITTED");
    }
}
