package com.studentleague.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Long retryAfterSeconds;

    public ApiException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public ApiException(String code, String message, HttpStatus status, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static ApiException conflict(String message) {
        return new ApiException("CONFLICT", message, HttpStatus.CONFLICT);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static ApiException badRequest(String message) {
        return new ApiException("BUSINESS_RULE_VIOLATION", message, HttpStatus.BAD_REQUEST);
    }

    public static ApiException rateLimited(String message) {
        return rateLimited(message, 60);
    }

    public static ApiException rateLimited(String message, long retryAfterSeconds) {
        long retry = Math.max(1, retryAfterSeconds);
        return new ApiException("RATE_LIMITED", message, HttpStatus.TOO_MANY_REQUESTS, retry);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException("SERVICE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
