package org.example.temperaturecontrolsystem.service;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.entity.SchedulerBoardRecord;
import org.example.temperaturecontrolsystem.mapper.RoomInfoMapper;
import org.example.temperaturecontrolsystem.mapper.SchedulerMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final SchedulerMapper schedulerMapper;
    private final RoomInfoMapper roomInfoMapper;

    /**
     * (对应新查询 1)
     * 获取某个指定房间的所有调度记录。
     *
     * @param roomId 房间号
     * @return 该房间的所有调度记录列表
     */
    public List<SchedulerBoardRecord> getAllRecordsForRoom(int roomId) {
        roomInfoMapper.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("Room with ID " + roomId + " not found."));

        return schedulerMapper.findAllRecordsByRoomId(roomId);
    }

    /**
     * (对应新查询 2)
     * 获取所有房间在某个时间范围内的调度记录。
     *
     * @param startTime 查询范围的开始时间
     * @param endTime   查询范围的结束时间
     * @return 该时间范围内的所有调度记录列表
     */
    public List<SchedulerBoardRecord> getRecordsInTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time cannot be after end time.");
        }

        return schedulerMapper.findRecordsInTimeRange(startTime, endTime);
    }
}
