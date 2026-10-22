package com.stockflow.auth;

import com.stockflow.store.StoreRole;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(
        UUID userId,
        UUID storeId,
        String email,
        String displayName,
        StoreRole role) implements Serializable {

    public List<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
