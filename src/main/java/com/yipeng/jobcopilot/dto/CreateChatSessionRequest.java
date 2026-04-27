// ── CreateChatSessionRequest.java ─────────────────────────────────────────────
package com.yipeng.jobcopilot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CreateChatSessionRequest {

    @NotNull
    private Long resumeId;

    @NotNull
    private Long jobPostId;
}