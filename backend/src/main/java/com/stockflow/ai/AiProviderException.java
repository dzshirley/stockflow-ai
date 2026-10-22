package com.stockflow.ai;

import com.stockflow.common.error.ApiException;
import org.springframework.http.HttpStatus;

class AiProviderException extends ApiException {

    AiProviderException(String message, Throwable cause) {
        this(HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE", message, cause);
    }

    AiProviderException(HttpStatus status, String code, String message, Throwable cause) {
        super(status, code, message);
        initCause(cause);
    }
}
