package org.example.temperaturecontrolsystem.service;

import org.example.temperaturecontrolsystem.entity.UserEntity;
import org.example.temperaturecontrolsystem.exception.InvalidCredentialsException;
import org.example.temperaturecontrolsystem.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthService {

    private final UserMapper userMapper;

    @Autowired
    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 认证逻辑。现在它调用mapper来获取用户数据。
     * @param account  用户名
     * @param password 密码
     * @return 用户身份
     * @throws InvalidCredentialsException 认证失败时抛出
     */
    public String authenticate(String account, String password) {

        UserEntity user = userMapper.findByAccount(account)
                .orElseThrow(() -> new InvalidCredentialsException("用户名或密码错误"));

        // 校验密码
        if (!Objects.equals(user.getPasswordColumn(), password)) {
            throw new InvalidCredentialsException("用户名或密码错误");
        }

        // 认证成功，返回身份
        return user.getRoleColumn();
    }
}
