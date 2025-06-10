package org.example.temperaturecontrolsystem.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoomInfo {
    private Integer roomId;
    private String clientId;
    private String clientName;
    private LocalDateTime checkinTime;
    private LocalDateTime checkoutTime;
    private Integer state;
    private Integer acState;
    private String currentSpeed;
    private Double currentTempera;
    private Double targetTempera;
}
