package org.example.temperaturecontrolsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.dto.*;
import org.example.temperaturecontrolsystem.service.AirConditionerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ac")
@RequiredArgsConstructor
public class AirConditionerController {
    private final AirConditionerService airConditionerService;

    @PostMapping("/{roomNumber}/turn-on")
    public ResponseEntity<?> turnOn(@PathVariable int roomNumber) {
        try {
            airConditionerService.turnOn(roomNumber);
            return ResponseEntity.ok(Map.of("message", "Air conditioner in room " + roomNumber + " turned on successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @PostMapping("/{roomNumber}/turn-off")
    public ResponseEntity<?> turnOff(@PathVariable int roomNumber) {
        try {
            airConditionerService.turnOff(roomNumber);
            return ResponseEntity.ok(Map.of("message", "Air conditioner in room " + roomNumber + " turned off successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    // 对于需要其他参数的请求，我们将路径变量和请求体结合使用
    @PostMapping("/{roomNumber}/set-speed")
    public ResponseEntity<?> setSpeed(@PathVariable int roomNumber, @RequestBody SetSpeedRequest request) {
        try {
            airConditionerService.setSpeed(roomNumber, request.getSpeed());
            return ResponseEntity.ok(Map.of("message", "Speed set successfully for room " + roomNumber + "."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @PostMapping("/{roomNumber}/set-temperature")
    public ResponseEntity<?> setTemperature(@PathVariable int roomNumber, @RequestBody SetTemperaRequest request) {
        try {
            airConditionerService.setTemperature(roomNumber, request.getTemperature());
            return ResponseEntity.ok(Map.of("message", "Temperature set successfully for room " + roomNumber + "."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }
}