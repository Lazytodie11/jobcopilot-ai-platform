package com.yipeng.jobcopilot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParsedJobPostResponse {

    private String url;
    private String pageTitle;
    private String companyName;
    private String jobTitle;
    private String description;
}