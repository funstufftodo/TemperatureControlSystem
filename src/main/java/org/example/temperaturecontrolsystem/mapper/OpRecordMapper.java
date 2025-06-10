package org.example.temperaturecontrolsystem.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.example.temperaturecontrolsystem.entity.OpRecord;

@Mapper
public interface OpRecordMapper {

    @Insert("INSERT INTO op_records (room_id, op_time, op_type, old_state, new_state) " +
            "VALUES (#{roomId}, #{opTime}, #{opType}, #{oldState}, #{newState})")
    void insert(OpRecord record);
}