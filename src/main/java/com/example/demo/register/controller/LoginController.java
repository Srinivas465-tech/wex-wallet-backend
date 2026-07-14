package com.example.demo.register.controller;

import com.example.demo.register.dto.LoginDto;
import com.example.demo.register.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService registerService;

    @PostMapping
    public LoginDto.LoginResponse createUser(@RequestBody LoginDto.LoginRequest request) {
        return registerService.createUser(request);
    }

    @GetMapping("/{userId}")
    public LoginDto.LoginResponse getUser(@PathVariable Long userId) {
        return registerService.getUser(userId);
    }

}
