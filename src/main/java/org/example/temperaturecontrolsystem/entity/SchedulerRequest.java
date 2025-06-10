package org.example.temperaturecontrolsystem.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SchedulerRequest {
    private int roomId;
    private String type;
    private String speed;
}
