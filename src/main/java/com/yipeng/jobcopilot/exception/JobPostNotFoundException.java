package com.yipeng.jobcopilot.exception;

public class JobPostNotFoundException extends RuntimeException {

    public JobPostNotFoundException(String message) {
        super(message);
    }
}