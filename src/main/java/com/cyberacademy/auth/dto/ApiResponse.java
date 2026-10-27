package com.cyberacademy.auth.dto;

import java.time.OffsetDateTime;

public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private OffsetDateTime timestamp = OffsetDateTime.now();

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public OffsetDateTime getTimestamp() { return timestamp; }
}