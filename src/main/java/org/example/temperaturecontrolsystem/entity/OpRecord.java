package org.example.temperaturecontrolsystem.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class OpRecord {
    private Long id;
    private Integer roomId;
    private LocalDateTime opTime;
    private Integer opType;
    private String oldState;
    private String newState;

    public OpRecord(Integer roomId, LocalDateTime opTime, Integer opType, String oldState, String newState) {
        this.roomId = roomId;
        this.opTime = opTime;
        this.opType = opType;
        this.oldState = oldState;
        this.newState = newState;
    }
}