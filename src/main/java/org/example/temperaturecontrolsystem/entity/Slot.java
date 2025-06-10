package org.example.temperaturecontrolsystem.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Slot implements Comparable<Slot> {
    private int roomId;
    private int speed;

    private LocalDateTime serviceStartTime;

    private LocalDateTime creationTime = LocalDateTime.now();

    @Override
    public int compareTo(Slot other) {
        return Integer.compare(other.speed, this.speed);
    }
}
