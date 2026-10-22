package com.stockflow.auth;

import com.stockflow.store.StoreRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthModels {

    private AuthModels() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 160) String storeName,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotBlank @Size(max = 64) String timeZone) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 72) String password,
            UUID storeId) {
    }

    public record AuthResponse(
            UUID userId,
            UUID storeId,
            String email,
            String displayName,
            StoreRole role) {

        public static AuthResponse from(AuthenticatedUser user) {
            return new AuthResponse(
                    user.userId(), user.storeId(), user.email(), user.displayName(), user.role());
        }
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }
}
