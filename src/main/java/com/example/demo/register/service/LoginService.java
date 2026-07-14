package com.example.demo.register.service;

import com.example.demo.authservice.JwtService;
import com.example.demo.register.dto.LoginDto;
import com.example.demo.register.model.User;
import com.example.demo.register.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public LoginDto.LoginResponse createUser(LoginDto.LoginRequest request) {

        Optional<User> optionalUser = userRepository.findByUserNameAndPassword(request.userName(), request.password());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            return buildLoginResponse(user);
        }
        User user = new User();
        user.setUserName(request.userName());
        user.setPassword(request.password());
        user = userRepository.save(user);
        return buildLoginResponse(user);
    }

    public LoginDto.LoginResponse buildLoginResponse(User user) {
        return new LoginDto.LoginResponse(user.getId(), user.getUserName(), user.getPassword());
    }

    public LoginDto.LoginResponse getUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return buildLoginResponse(user);
    }
}
