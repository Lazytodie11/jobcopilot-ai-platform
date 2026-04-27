package com.yipeng.jobcopilot.exception;

public class ResumeEmailAlreadyExistsException extends RuntimeException {

    public ResumeEmailAlreadyExistsException(String message) {
        super(message);
    }
}