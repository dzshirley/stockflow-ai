package com.stockflow.common.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public URI type() {
        return URI.create("https://stockflow.local/problems/" + code.toLowerCase().replace('_', '-'));
    }
}
