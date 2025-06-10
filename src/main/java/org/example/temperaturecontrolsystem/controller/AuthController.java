package org.example.temperaturecontrolsystem.controller;

import org.example.temperaturecontrolsystem.dto.LoginRequest;
import org.example.temperaturecontrolsystem.dto.LoginResponse;
import org.example.temperaturecontrolsystem.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        String identity = authService.authenticate(loginRequest.getAccount(), loginRequest.getPassword());

        System.out.println("Login successful");
        LoginResponse response = new LoginResponse("Login successful", identity);

        return ResponseEntity.ok(response);
    }
}
