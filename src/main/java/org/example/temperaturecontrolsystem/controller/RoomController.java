package org.example.temperaturecontrolsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.dto.CheckOutBillResponse;
import org.example.temperaturecontrolsystem.dto.RoomStatusResponse;
import org.example.temperaturecontrolsystem.dto.UpdateCurrentTemperatureRequest;
import org.example.temperaturecontrolsystem.entity.RoomInfo;
import org.example.temperaturecontrolsystem.service.RoomStateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomStateService roomStateService;

    @GetMapping("/status")
    public ResponseEntity<List<RoomInfo>> getAllRooms() {
        List<RoomInfo> rooms = roomStateService.getAllRooms();
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomId}/status")
    public ResponseEntity<?> getRoomStatus(@PathVariable int roomId) {
        try {
            RoomStatusResponse status = roomStateService.getRoomStatus(roomId);
            return ResponseEntity.ok(status);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }
    @GetMapping("/{roomId}/ac_state")
    public ResponseEntity<?> getAcState(@PathVariable int roomId) {
        try {
            Integer acState = roomStateService.getAcState(roomId);
            return ResponseEntity.ok(acState);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @PutMapping("/{roomId}/current-temperature")
    public ResponseEntity<?> updateCurrentTemperature(
            @PathVariable int roomId,
            @RequestBody UpdateCurrentTemperatureRequest request) {
        try {
            roomStateService.updateCurrentTemperature(roomId, request.getCurrentTemperature());
            return ResponseEntity.ok(Map.of("message", "Current temperature updated successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @GetMapping("/{roomId}/bill")
    public ResponseEntity<?> getCheckOutBill(@PathVariable int roomId) {
        try {
            CheckOutBillResponse bill = roomStateService.getCheckOutBill(roomId);
            return ResponseEntity.ok(bill);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @GetMapping("/{roomId}/detail_bill")
    public ResponseEntity<?> getDetailCheckOutBill(@PathVariable int roomId) {
        try {
            CheckOutBillResponse bill = roomStateService.getDetailCheckOutBill(roomId);
            return ResponseEntity.ok(bill);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }
}
