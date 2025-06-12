package org.example.temperaturecontrolsystem.service;

import lombok.RequiredArgsConstructor;
import org.example.temperaturecontrolsystem.dto.CheckOutBillResponse;
import org.example.temperaturecontrolsystem.dto.RoomStatusResponse;
import org.example.temperaturecontrolsystem.entity.RoomInfo;
import org.example.temperaturecontrolsystem.entity.SchedulerBoardRecord;
import org.example.temperaturecontrolsystem.mapper.RoomInfoMapper;
import org.example.temperaturecontrolsystem.mapper.SchedulerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomStateService {
    private final RoomInfoMapper roomInfoMapper;
    private final SchedulerMapper schedulerMapper;

    private static final BigDecimal DAILY_ROOM_RATE = new BigDecimal("298.00");

    public RoomStatusResponse getRoomStatus(int roomId) {
        RoomInfo roomInfo = roomInfoMapper.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("Room " + roomId + " not found."));

        BigDecimal totalCost = BigDecimal.ZERO;

        if (roomInfo.getState() == 1 && roomInfo.getCheckinTime() != null) {
            totalCost = schedulerMapper.getTotalCostByRoomIdSince(roomId, roomInfo.getCheckinTime())
                    .orElse(BigDecimal.ZERO);
        }
        RoomStatusResponse response = new RoomStatusResponse();
        response.setRoomId(roomInfo.getRoomId());
        response.setOccupancyState(roomInfo.getState());
        response.setAcState(roomInfo.getAcState());
        response.setCurrentSpeed(roomInfo.getCurrentSpeed());
        response.setTargetTemperature(roomInfo.getTargetTempera());
        response.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));

        return response;
    }



    public Integer getAcState(int roomId) {
        RoomInfo room = roomInfoMapper.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("Room " + roomId + " not found."));

        return room.getAcState();
    }

    @Transactional
    public void updateCurrentTemperature(int roomId, double newCurrentTemperature) {

        int updatedRows = roomInfoMapper.updateCurrentTemperature(roomId, newCurrentTemperature);

        if (updatedRows == 0) {
            throw new IllegalStateException("Room " + roomId + " not found, cannot update current temperature.");
        }

        System.out.println("Successfully updated current temperature for room " + roomId + " to " + newCurrentTemperature);
    }

    public CheckOutBillResponse getCheckOutBill(int roomId) {
        RoomInfo roomInfo = roomInfoMapper.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("Room " + roomId + " not found."));

        if (roomInfo.getState() != 0 || roomInfo.getCheckoutTime() == null || roomInfo.getCheckinTime() == null) {
            throw new IllegalStateException("Cannot generate a bill for room " + roomId + ". Check-in/out information is incomplete.");
        }

        LocalDateTime checkinTime = roomInfo.getCheckinTime();
        LocalDateTime checkoutTime = roomInfo.getCheckoutTime();

        BigDecimal roomFee = calculateRoomFee(checkinTime, checkoutTime);

        BigDecimal totalAcCost = schedulerMapper.getTotalCostByRoomIdSince(roomId, roomInfo.getCheckinTime())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalBill = roomFee.add(totalAcCost);

        CheckOutBillResponse bill = new CheckOutBillResponse();
        bill.setRoomId(roomInfo.getRoomId());
        bill.setClientName(roomInfo.getClientName());
        bill.setClientId(roomInfo.getClientId());
        bill.setCheckinTime(checkinTime);
        bill.setCheckoutTime(checkoutTime);

        bill.setRoomFee(roomFee); // 设置房费
        bill.setTotalAcCost(totalAcCost);
        bill.setTotalBill(totalBill); // 设置总账单

        return bill;
    }

    public CheckOutBillResponse getDetailCheckOutBill(int roomId) {
        RoomInfo roomInfo = roomInfoMapper.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("Room " + roomId + " not found."));

        if (roomInfo.getState() != 0 || roomInfo.getCheckoutTime() == null || roomInfo.getCheckinTime() == null) {
            throw new IllegalStateException("Cannot generate a bill for room " + roomId + ". Check-in/out information is incomplete.");
        }

        LocalDateTime checkinTime = roomInfo.getCheckinTime();
        LocalDateTime checkoutTime = roomInfo.getCheckoutTime();

        List<SchedulerBoardRecord> details = schedulerMapper.findRecordsByRoomIdSince(roomId, checkinTime);


        BigDecimal roomFee = calculateRoomFee(checkinTime, checkoutTime);

        BigDecimal totalAcCost = schedulerMapper.getTotalCostByRoomIdSince(roomId, roomInfo.getCheckinTime())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalBill = roomFee.add(totalAcCost);

        CheckOutBillResponse bill = new CheckOutBillResponse();
        bill.setRoomId(roomInfo.getRoomId());
        bill.setClientName(roomInfo.getClientName());
        bill.setClientId(roomInfo.getClientId());
        bill.setCheckinTime(checkinTime);
        bill.setCheckoutTime(checkoutTime);

        bill.setRoomFee(roomFee); // 设置房费
        bill.setTotalAcCost(totalAcCost);
        bill.setTotalBill(totalBill); // 设置总账单

        bill.setDetails(details);

        return bill;
    }

    private BigDecimal calculateRoomFee(LocalDateTime checkinTime, LocalDateTime checkoutTime) {
        if (checkoutTime.isBefore(checkinTime)) {
            return DAILY_ROOM_RATE;
        }

        java.time.LocalDate checkinDate = checkinTime.toLocalDate();

        java.time.LocalDate checkoutDate = checkoutTime.toLocalDate();

        // 计算两个日期之间相差的天数
        long daysDiff = ChronoUnit.DAYS.between(checkinDate, checkoutDate);


        long daysToBill;
        if (checkoutTime.toLocalTime().isAfter(java.time.LocalTime.MIDNIGHT) || daysDiff == 0) {
            daysToBill = daysDiff + 1;
        } else {
            daysToBill = daysDiff;
        }

        if (daysToBill <= 0) {
            daysToBill = 1;
        }

        return DAILY_ROOM_RATE.multiply(new BigDecimal(daysToBill));
    }


    public List<RoomInfo> getAllRooms() {
        return roomInfoMapper.findAll();
    }
}
