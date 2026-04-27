package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateResumeRequest {

    @NotBlank(message = "Title cannot be blank")
    private String title;

    @NotBlank(message = "Candidate name cannot be blank")
    private String candidateName;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email format is invalid")
    private String email;

    private String content;
}