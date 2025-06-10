package org.example.temperaturecontrolsystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.temperaturecontrolsystem.entity.UserEntity;

import java.util.Optional;

@Mapper
public interface UserMapper {

    /**
     * 根据账户名从数据库中查找用户。
     *
     * @param account 账户名
     * @return 返回一个包含 UserEntity 的 Optional。如果数据库中没有找到匹配的用户，则返回 Optional.empty()。
     */
    @Select("SELECT * FROM users WHERE account_column = #{account}")
    Optional<UserEntity> findByAccount(@Param("account") String account);

}