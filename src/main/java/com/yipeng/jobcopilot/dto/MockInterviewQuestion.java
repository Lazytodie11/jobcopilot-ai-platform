package com.yipeng.jobcopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MockInterviewQuestion {

    private String category;   // e.g. "Behavioral", "Technical", "Language/Go"
    private String question;
    private String whyAsked;   // why this question is relevant to this specific JD + resume
    private String answerHint; // concrete tips for answering, referencing resume content
}