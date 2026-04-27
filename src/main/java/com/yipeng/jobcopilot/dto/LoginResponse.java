package com.yipeng.jobcopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class LoginResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String token;
    private String message;
}