package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.CreateUserRequest;
import com.yipeng.jobcopilot.dto.LoginRequest;
import com.yipeng.jobcopilot.dto.LoginResponse;
import com.yipeng.jobcopilot.dto.UpdateUserRequest;
import com.yipeng.jobcopilot.dto.UserResponse;
import com.yipeng.jobcopilot.entity.User;
import com.yipeng.jobcopilot.exception.InvalidCredentialsException;
import com.yipeng.jobcopilot.exception.UserEmailAlreadyExistsException;
import com.yipeng.jobcopilot.exception.UserNotFoundException;
import com.yipeng.jobcopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserResponse createUser(CreateUserRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            throw new UserEmailAlreadyExistsException("User email already exists: " + request.getEmail());
        });

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);
        return toUserResponse(savedUser);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        return toUserResponse(user);
    }

    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        return toUserResponse(user);
    }

    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (!user.getId().equals(id)) {
                throw new UserEmailAlreadyExistsException("User email already exists: " + request.getEmail());
            }
        });

        existingUser.setFullName(request.getFullName());
        existingUser.setEmail(request.getEmail());
        existingUser.setPassword(passwordEncoder.encode(request.getPassword()));

        User updatedUser = userRepository.save(existingUser);
        return toUserResponse(updatedUser);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!passwordMatches) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return LoginResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .token(token)
                .message("Login successful")
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}