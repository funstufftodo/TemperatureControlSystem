package org.example.temperaturecontrolsystem.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.entity.SchedulerRequest;
import org.example.temperaturecontrolsystem.entity.OpRecord;
import org.example.temperaturecontrolsystem.entity.RoomInfo;
import org.example.temperaturecontrolsystem.mapper.OpRecordMapper;
import org.example.temperaturecontrolsystem.mapper.RoomInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AirConditionerService {
    private final RoomInfoMapper roomInfoMapper;
    private final OpRecordMapper opRecordMapper;
    private final SchedulerService schedulerService;

    private static final int STATE_ON = 1;
    private static final int STATE_OFF = 0;

    private static final int OP_TYPE_POWER_ON = 1;
    private static final int OP_TYPE_POWER_OFF = 2;
    private static final int OP_TYPE_TEMP = 3;
    private static final int OP_TYPE_SPEED = 4;

    @Transactional
    public void turnOn(int roomNumber) {
        RoomInfo room = roomInfoMapper.findById(roomNumber)
                .orElseThrow(() -> new IllegalStateException("Room " + roomNumber + " not found."));

        if (room.getAcState() == STATE_ON) {
            return;
        }

        int updatedRows = roomInfoMapper.updateAcStateIfEquals(roomNumber, STATE_ON);

        OpRecord record = new OpRecord(
                roomNumber,
                LocalDateTime.now(),
                OP_TYPE_POWER_ON,
                "AC_STATE_OFF", // 旧状态是 1
                "AC_STATE_ON"
        );
        opRecordMapper.insert(record);
        schedulerService.submitMsg(new SchedulerRequest(roomNumber, "add", "medium"));
    }

    @Transactional
    public void turnOff(int roomNumber) {
        RoomInfo room = roomInfoMapper.findById(roomNumber)
                .orElseThrow(() -> new IllegalStateException("Room " + roomNumber + " not found."));

        if (room.getAcState() == STATE_OFF) {
            return;
        }

        int updatedRows = roomInfoMapper.updateAcStateIfEquals(roomNumber, STATE_OFF);

        OpRecord record = new OpRecord(
                roomNumber,
                LocalDateTime.now(),
                OP_TYPE_POWER_OFF,
                "AC_STATE_ON",
                "AC_STATE_OFF"
        );
        opRecordMapper.insert(record);
        schedulerService.submitMsg(new SchedulerRequest(roomNumber, "delete", null));
    }

    @Transactional
    public void setSpeed(int roomNumber, String newSpeed) {

        RoomInfo room = roomInfoMapper.findById(roomNumber)
                .orElseThrow(() -> new IllegalStateException("Room " + roomNumber + " not found."));

        String oldSpeed = room.getCurrentSpeed();

        if (newSpeed.equalsIgnoreCase(oldSpeed)) {
            return;
        }

        roomInfoMapper.updateSpeed(roomNumber, newSpeed);

        opRecordMapper.insert(new OpRecord(roomNumber, LocalDateTime.now(), OP_TYPE_SPEED, oldSpeed, newSpeed));

        schedulerService.submitMsg(new SchedulerRequest(roomNumber, "update", newSpeed));
        System.out.println("Room " + roomNumber + " speed updated from '" + oldSpeed + "' to '" + newSpeed + "'. Update message sent to scheduler.");
    }

    @Transactional
    public void setTemperature(int roomNumber, double newTemperature) {
        RoomInfo room = roomInfoMapper.findById(roomNumber)
                .orElseThrow(() -> new IllegalStateException("Room " + roomNumber + " not found."));


        double oldTemperature = room.getTargetTempera();

        if (Math.abs(newTemperature - oldTemperature) < 0.01) {
            return;
        }

        roomInfoMapper.updateTargetTemperature(roomNumber, newTemperature);

        opRecordMapper.insert(new OpRecord(
                roomNumber,
                LocalDateTime.now(),
                OP_TYPE_TEMP,
                String.valueOf(oldTemperature),
                String.valueOf(newTemperature)
        ));

        System.out.println("Room " + roomNumber + " temperature updated from '" + oldTemperature + "' to '" + newTemperature + "'.");
    }

    @PreDestroy
    @Transactional
    public void cleanupOnShutdown() {
        System.out.println("Application is shutting down. Gracefully turning off all active air conditioners...");

        List<Integer> activeRoomIds = roomInfoMapper.findAllActiveAcRoomIds();

        if (activeRoomIds.isEmpty()) {
            return;
        }

        System.out.println("Found " + activeRoomIds.size() + " active air conditioner(s) to turn off: " + activeRoomIds);

        for (Integer roomId : activeRoomIds) {
            try {
                this.turnOff(roomId);
            } catch (Exception e) {
                // 在循环中捕获异常，防止一个房间的关闭失败影响到其他房间
            }
        }
        System.out.println("Finished shutdown cleanup task for air conditioners.");
    }
}
