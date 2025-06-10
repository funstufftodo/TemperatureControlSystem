package org.example.temperaturecontrolsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.dto.*;
import org.example.temperaturecontrolsystem.service.AirConditionerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/ac")
@RequiredArgsConstructor
public class AirConditionerController {
    private final AirConditionerService airConditionerService;

    @PostMapping("/turn-on")
    public ResponseEntity<?> turnOn(@RequestBody TurnOnOffRequest request) {
        try {
            airConditionerService.turnOn(request.getRoomNumber());
            return ResponseEntity.ok(Map.of("message", "Air conditioner turned on successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @PostMapping("/turn-off")
    public ResponseEntity<?> turnOff(@RequestBody TurnOnOffRequest request) {
        try {
            airConditionerService.turnOff(request.getRoomNumber());
            return ResponseEntity.ok(Map.of("message", "Air conditioner turned off successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @PostMapping("/set-speed")
    public ResponseEntity<?> setSpeed(@RequestBody SetSpeedRequest request) {
        try {
            airConditionerService.setSpeed(request.getRoomNumber(), request.getSpeed());
            return ResponseEntity.ok(Map.of("message", "Speed set successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @PostMapping("/set-temperature")
    public ResponseEntity<?> setTemperature(@RequestBody SetTemperaRequest request) {
        try {
            airConditionerService.setTemperature(request.getRoomNumber(), request.getTemperature());
            return ResponseEntity.ok(Map.of("message", "Temperature set successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }
}