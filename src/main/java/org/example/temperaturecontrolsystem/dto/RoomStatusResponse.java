package org.example.temperaturecontrolsystem.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class RoomStatusResponse {
    private int roomId;
    private int occupancyState; // 0=空闲, 1=已入住
    private int acState;        // 0=关机, 1=开机
    private String currentSpeed;
    //private double currentTemperature; // 当前实际温度
    private double targetTemperature;  // 目标温度
    private BigDecimal totalCost;      // 累计费用
}