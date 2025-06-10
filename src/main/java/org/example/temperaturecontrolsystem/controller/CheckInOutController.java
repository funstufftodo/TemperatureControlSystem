package org.example.temperaturecontrolsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.dto.CheckInRequest;
import org.example.temperaturecontrolsystem.dto.CheckOutRequest;
import org.example.temperaturecontrolsystem.service.CheckInOutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckInOutController {
    private final CheckInOutService checkInOutService;

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody CheckInRequest request) {
        try {
            checkInOutService.checkIn(request);
            return ResponseEntity.ok(Map.of("message", "Check in successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(@RequestBody CheckOutRequest request) {
        try {
            checkInOutService.checkOut(request);
            return ResponseEntity.ok(Map.of("message", "Check out successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
