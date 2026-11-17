package com.secmulher.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.domain.dtos.AuthDtos.ChangePasswordRequest;
import com.secmulher.domain.dtos.AuthDtos.LoginRequest;
import com.secmulher.domain.dtos.AuthDtos.LoginResponse;
import com.secmulher.domain.dtos.AuthDtos.MeResponse;
import com.secmulher.domain.services.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {
    final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
