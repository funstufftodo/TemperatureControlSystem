package org.example.temperaturecontrolsystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.temperaturecontrolsystem.entity.SchedulerBoardRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface SchedulerMapper {
    @Insert("INSERT INTO scheduler_board (room_id, start_time, end_time, duration_seconds, speed, cost) " +
            "VALUES (#{roomId}, #{startTime}, #{endTime}, #{durationSeconds}, #{speed}, #{cost})")
    void insertRecord(SchedulerBoardRecord record);

    @Select("SELECT SUM(cost) FROM scheduler_board " +
            "WHERE room_id = #{roomId} AND start_time >= #{checkinTime}")
    Optional<BigDecimal> getTotalCostByRoomIdSince(@Param("roomId") int roomId,
                                                   @Param("checkinTime") LocalDateTime checkinTime);

    @Select("SELECT " +
            "room_id, " +
            "start_time, " +
            "end_time, " +
            "duration_seconds, " +
            "speed, " +
            "cost " +
            "FROM scheduler_board " +
            "WHERE room_id = #{roomId} AND start_time >= #{checkinTime} " +
            "ORDER BY start_time ASC")
    List<SchedulerBoardRecord> findRecordsByRoomIdSince(
            @Param("roomId") int roomId,
            @Param("checkinTime") LocalDateTime checkinTime
    );

    @Select("SELECT room_id, start_time, end_time, duration_seconds, speed, cost " +
            "FROM scheduler_board " +
            "WHERE room_id = #{roomId} " +
            "ORDER BY start_time ASC")
    List<SchedulerBoardRecord> findAllRecordsByRoomId(@Param("roomId") int roomId);

    @Select("SELECT room_id, start_time, end_time, duration_seconds, speed, cost " +
            "FROM scheduler_board " +
            "WHERE start_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY room_id ASC, start_time ASC")
    List<SchedulerBoardRecord> findRecordsInTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}