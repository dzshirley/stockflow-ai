package com.stockflow.store;

import com.stockflow.auth.AuthenticatedUser;
import com.stockflow.common.error.ApiExceptions;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class StoreContextProvider {

    public StoreContext current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw ApiExceptions.unauthorized("Authentication is required");
        }
        return new StoreContext(principal.userId(), principal.storeId(), principal.role());
    }
}
