package org.example.temperaturecontrolsystem.dto;

import lombok.Data;

@Data
public class SetTemperaRequest {
    private int roomNumber;
    private double temperature;
}