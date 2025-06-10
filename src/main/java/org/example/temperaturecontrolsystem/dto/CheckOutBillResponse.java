package org.example.temperaturecontrolsystem.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.example.temperaturecontrolsystem.entity.SchedulerBoardRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CheckOutBillResponse {

    private int roomId;
    private String clientName;
    private String clientId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkinTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkoutTime;

    private BigDecimal totalAcCost;
    private BigDecimal roomFee;
    private BigDecimal totalBill;

    private List<SchedulerBoardRecord> details;
}