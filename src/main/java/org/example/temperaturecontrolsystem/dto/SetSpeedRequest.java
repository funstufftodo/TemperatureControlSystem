package org.example.temperaturecontrolsystem.dto;

import lombok.Data;

@Data
public class SetSpeedRequest {
    private int roomNumber;
    private String speed;
}
