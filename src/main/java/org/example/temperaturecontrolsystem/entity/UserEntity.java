package org.example.temperaturecontrolsystem.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
    private String accountColumn;
    private String passwordColumn;
    private String roleColumn;
}
