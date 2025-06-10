package org.example.temperaturecontrolsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.entity.SchedulerBoardRecord;
import org.example.temperaturecontrolsystem.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports") // API 基础路径
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 获取某个指定房间的所有调度记录。
     * GET /api/reports/rooms/{roomId}
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<?> getAllRecordsForRoom(@PathVariable int roomId) {
        try {
            List<SchedulerBoardRecord> records = reportService.getAllRecordsForRoom(roomId);
            return ResponseEntity.ok(records);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取所有房间在某个时间范围内的调度记录。
     * GET /api/reports/usage?startTime=2023-10-27T10:00:00&endTime=2023-10-28T10:00:00
     */
    @GetMapping("/usage")
    public ResponseEntity<?> getRecordsInTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<SchedulerBoardRecord> records = reportService.getRecordsInTimeRange(startTime, endTime);
            return ResponseEntity.ok(records);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}