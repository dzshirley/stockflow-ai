package com.stockflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockflow.store.StoreMembership;
import com.stockflow.store.StoreMembershipRepository;
import com.stockflow.store.StoreRepository;
import com.stockflow.store.StoreRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock StoreRepository storeRepository;
    @Mock StoreMembershipRepository membershipRepository;
    @Mock PasswordEncoder passwordEncoder;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                userRepository,
                storeRepository,
                membershipRepository,
                passwordEncoder,
                Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registrationNormalizesIdentityAndCreatesAdminOwnership() {
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("a-secure-password")).thenReturn("encoded");

        var principal = service.register(new AuthModels.RegisterRequest(
                " Owner@Example.com ",
                "a-secure-password",
                "Owner",
                "Main Store",
                "usd",
                "America/New_York"));

        assertThat(principal.email()).isEqualTo("owner@example.com");
        assertThat(principal.role()).isEqualTo(StoreRole.ADMIN);
        var membership = ArgumentCaptor.forClass(StoreMembership.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().getStoreId()).isEqualTo(principal.storeId());
        assertThat(membership.getValue().getUserId()).isEqualTo(principal.userId());
        verify(userRepository).save(any(AppUser.class));
        verify(storeRepository).save(any());
    }
}
