package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SendMessageRequest {

    @NotBlank
    private String content;
}