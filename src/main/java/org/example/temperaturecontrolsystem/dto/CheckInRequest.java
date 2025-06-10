package org.example.temperaturecontrolsystem.dto;

import lombok.Data;

@Data
public class CheckInRequest {
    private int roomNumber;
    private String clientName;
    private String clientID;
}
