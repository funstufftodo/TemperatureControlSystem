package org.example.temperaturecontrolsystem.service;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.dto.CheckInRequest;
import org.example.temperaturecontrolsystem.dto.CheckOutRequest;
import org.example.temperaturecontrolsystem.entity.SchedulerRequest;
import org.example.temperaturecontrolsystem.entity.RoomInfo;
import org.example.temperaturecontrolsystem.mapper.RoomInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckInOutService {
    private final RoomInfoMapper roomInfoMapper;
    private final AirConditionerService airConditionerService;
    private final SchedulerService schedulerService;

    public void checkIn(CheckInRequest request) {
        RoomInfo roomInfo = new RoomInfo();
        roomInfo.setRoomId(request.getRoomNumber());
        roomInfo.setClientId(request.getClientID());
        roomInfo.setClientName(request.getClientName());
        roomInfo.setCheckinTime(LocalDateTime.now());

        int updatedRows = roomInfoMapper.updateForCheckIn(roomInfo);

        if (updatedRows > 0) {
            System.out.println("Successfully checked in by updating existing room " + request.getRoomNumber());
            return;
        }

        Optional<RoomInfo> roomOpt = roomInfoMapper.findById(request.getRoomNumber());

        if (roomOpt.isPresent()) {
            throw new IllegalStateException("Check-in failed: Room " + request.getRoomNumber() + " is already occupied.");
        } else {
            roomInfoMapper.insertForCheckIn(roomInfo);
            System.out.println("Successfully checked in by inserting new room " + request.getRoomNumber());
        }

        System.out.println("通知调度器：房间 " + request.getRoomNumber() + " 已入住。");
    }

    @Transactional
    public void checkOut(CheckOutRequest request) {
        LocalDateTime checkoutTime = LocalDateTime.now();
        int roomNumber = request.getRoomNumber();

        try {
            airConditionerService.turnOff(roomNumber);
            System.out.println("Air conditioner for room " + roomNumber + " was turned off during checkout.");
        } catch (IllegalStateException e) {
            System.out.println("Info during checkout for room " + roomNumber + ": " + e.getMessage());
        }
        int updatedRows = roomInfoMapper.updateForCheckOut(roomNumber, LocalDateTime.now());

        if (updatedRows == 0) {
            throw new IllegalStateException("Checkout failed: Room " + roomNumber + " is not currently occupied or does not exist.");
        }

        schedulerService.submitMsg(new SchedulerRequest(roomNumber, "delete", null));
        System.out.println("通知结算系统：为房间 " + request.getRoomNumber() + " 进行结算。");
    }
}
