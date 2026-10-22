package com.stockflow.store;

import com.stockflow.auth.AuthModels;
import java.util.List;
import java.util.UUID;

public final class StoreModels {

    private StoreModels() {
    }

    public record StoreResponse(UUID id, String name, String currencyCode, String timeZone, StoreRole role) {
    }

    public record MemberResponse(UUID userId, String email, String displayName, StoreRole role) {
    }

    public record MembersResponse(List<MemberResponse> items) {
    }

    public record CreateStaffRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
                    @jakarta.validation.constraints.Size(max = 320) String email,
            @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Size(min = 12, max = 72) String password,
            @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Size(max = 120) String displayName) {

        AuthModels.RegisterRequest asRegistration(Store store) {
            return new AuthModels.RegisterRequest(
                    email, password, displayName, store.getName(), store.getCurrencyCode(), store.getTimeZone());
        }
    }
}
