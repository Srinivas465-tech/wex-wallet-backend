package com.example.demo.register.dto;

import jakarta.validation.constraints.NotNull;

public record LoginDto() {

    public record LoginRequest(
            @NotNull String userName, @NotNull String password) {}

    public record LoginResponse(
            Long id,
            String userName,
            String password) {}
}
