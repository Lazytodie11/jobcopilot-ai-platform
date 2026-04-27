package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.NotBlank;

public class ParseJobUrlRequest {

    @NotBlank(message = "URL is required")
    private String url;

    public String getUrl() {
        return url;
    }
}