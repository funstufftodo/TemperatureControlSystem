package org.example.temperaturecontrolsystem.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SchedulerBoardRecord {
    private Long id;
    private Integer roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private Integer speed;
    private BigDecimal cost;

    public SchedulerBoardRecord(Integer roomId, LocalDateTime startTime, LocalDateTime endTime, Long durationSeconds, Integer speed, BigDecimal cost) {
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.speed = speed;
        this.cost = cost;
    }
}
