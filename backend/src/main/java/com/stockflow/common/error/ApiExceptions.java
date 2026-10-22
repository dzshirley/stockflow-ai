package com.stockflow.common.error;

import org.springframework.http.HttpStatus;

public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", detail);
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " was not found");
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }
}
