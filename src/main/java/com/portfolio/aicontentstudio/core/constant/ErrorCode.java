package com.portfolio.aicontentstudio.core.constant;

public enum ErrorCode {
    // System Errors
    INTERNAL_SERVER_ERROR(500, "SYS_001", "Internal server error occurred"),
    INVALID_INPUT(400, "SYS_002", "Invalid input parameters"),
    UNAUTHORIZED(401, "SYS_003", "Unauthorized access"),
    ACCESS_DENIED(403, "SYS_004", "Access is denied"),

    // User & Auth Errors
    USER_NOT_FOUND(404, "USER_001", "User not found"),
    USER_ALREADY_EXISTS(409, "USER_002", "User already exists"),
    EMAIL_ALREADY_EXISTS(409, "USER_003", "Email is already in use"),
    ROLE_NOT_FOUND(404, "USER_004", "Default role not found"),
    INVALID_REFRESH_TOKEN(401, "AUTH_001", "Invalid or expired refresh token"),
    INVALID_CREDENTIALS(401, "AUTH_002", "Invalid email or password");

    private final int status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(int status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
