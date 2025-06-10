package org.example.temperaturecontrolsystem.mapper;

import org.apache.ibatis.annotations.*;
import org.example.temperaturecontrolsystem.entity.RoomInfo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RoomInfoMapper {

    @Insert("INSERT INTO room_infos (room_id, client_id, client_name, checkin_time, " +
            "state, ac_state, current_speed, current_tempera, target_tempera) " +
            "VALUES (#{roomId}, #{clientId}, #{clientName}, #{checkinTime}, " +
            "1, 0, 'medium', 25.0, 25.0)")
    void insertForCheckIn(RoomInfo roomInfo);

    @Update("UPDATE room_infos SET " +
            "client_id = #{clientId}, " +
            "client_name = #{clientName}, " +
            "checkin_time = #{checkinTime}, " +
            "checkout_time = NULL, " + // 清空上次的退房时间
            "state = 1, " +   // 设置为入住状态
            "current_speed = 'medium', " +
            "target_tempera = 25.0 " +
            "WHERE room_id = #{roomId} AND state = 0")
    int updateForCheckIn(RoomInfo roomInfo);


    @Update("UPDATE room_infos SET " +
            "checkout_time = #{checkoutTime}, " +
            "state = 0 " + // 设置为空闲状态
            "WHERE room_id = #{roomNumber} AND state = 1")
    int updateForCheckOut(@Param("roomNumber") int roomNumber, @Param("checkoutTime") LocalDateTime checkoutTime);

    /**
     * 根据房间号查询房间信息
     * @param roomNumber 房间号
     * @return Optional 包装的 RoomInfo 对象
     */
    @Select("SELECT * FROM room_infos WHERE room_id = #{roomNumber}")
    Optional<RoomInfo> findById(@Param("roomNumber") int roomNumber);

    /**
     * 更新指定房间的状态
     * @param roomNumber 房间号
     * @param state 新的状态
     * @return 更新的行数，可以用来判断操作是否成功
     */
    @Update("UPDATE room_infos SET state = #{state} WHERE room_id = #{roomNumber}")
    int updateState(@Param("roomNumber") int roomNumber, @Param("state") int state);

    /**
     * 有条件地更新指定房间的状态
     * @param roomNumber 房间号
     * @param newState 新状态
     * @return 更新的行数。如果房间当前状态不等于 expectedOldState，则返回0，表示更新未发生。
     */
    @Update("UPDATE room_infos SET ac_state = #{newState} WHERE room_id = #{roomNumber} AND ac_state <> #{newState}")
    int updateAcStateIfEquals(@Param("roomNumber") int roomNumber, @Param("newState") int newState);
    /**
     * 更新指定房间的风速
     * @param roomNumber 房间号
     * @param speed 新的风速
     * @return 更新的行数
     */
    @Update("UPDATE room_infos SET current_speed = #{speed} WHERE room_id = #{roomNumber}")
    int updateSpeed(@Param("roomNumber") int roomNumber, @Param("speed") String speed);

    /**
     * 更新指定房间的温度
     * @param roomNumber 房间号
     * @param temperature 新的温度
     * @return 更新的行数
     */
    @Update("UPDATE room_infos SET current_tempera = #{temperature} WHERE room_id = #{roomNumber}")
    int updateCurrentTemperature(@Param("roomNumber") int roomNumber, @Param("temperature") double temperature);

    @Update("UPDATE room_infos SET target_tempera = #{targetTemperature} WHERE room_id = #{roomNumber}")
    int updateTargetTemperature(@Param("roomNumber") int roomNumber, @Param("targetTemperature") double targetTemperature);

    @Update("UPDATE room_infos SET ac_state = #{acState} WHERE room_id = #{roomId}")
    int updateAcState(@Param("roomId") int roomId, @Param("acState") int acState);

    @Select("SELECT room_id FROM room_infos WHERE ac_state <> 0")
    List<Integer> findAllActiveAcRoomIds();
}
