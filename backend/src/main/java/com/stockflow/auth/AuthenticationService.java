package com.stockflow.auth;

import com.stockflow.common.error.ApiExceptions;
import com.stockflow.store.Store;
import com.stockflow.store.StoreMembership;
import com.stockflow.store.StoreMembershipRepository;
import com.stockflow.store.StoreRepository;
import com.stockflow.store.StoreRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final String DUMMY_HASH = "$2a$12$wJpDg47BAM8YkQ/N3rGdnuu/MNDrvgBoWS.NpDmJhZ8bym.9bXH6e";

    private final AppUserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthenticationService(
            AppUserRepository userRepository,
            StoreRepository storeRepository,
            StoreMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedUser register(AuthModels.RegisterRequest request) {
        validatePassword(request.password());
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiExceptions.conflict("EMAIL_ALREADY_EXISTS", "An account already exists for this email");
        }

        var currency = normalizeCurrency(request.currencyCode());
        var timeZone = validateTimeZone(request.timeZone());
        var now = Instant.now(clock);
        var user = new AppUser(
                UUID.randomUUID(), email, passwordEncoder.encode(request.password()), request.displayName().trim(), now);
        var store = new Store(UUID.randomUUID(), request.storeName().trim(), currency, timeZone, now);
        var membership = new StoreMembership(
                UUID.randomUUID(), store.getId(), user.getId(), StoreRole.ADMIN, now);

        userRepository.save(user);
        storeRepository.save(store);
        membershipRepository.save(membership);
        return principal(user, membership);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(AuthModels.LoginRequest request) {
        var email = normalizeEmail(request.email());
        var user = userRepository.findByEmail(email).orElse(null);
        var hash = user == null ? DUMMY_HASH : user.getPasswordHash();
        if (!passwordEncoder.matches(request.password(), hash)
                || user == null
                || user.getStatus() != UserStatus.ACTIVE) {
            throw ApiExceptions.unauthorized("Invalid email or password");
        }

        StoreMembership membership;
        if (request.storeId() != null) {
            membership = membershipRepository.findByUserIdAndStoreId(user.getId(), request.storeId())
                    .orElseThrow(() -> ApiExceptions.forbidden("The account does not belong to that store"));
        } else {
            var memberships = membershipRepository.findByUserIdOrderByCreatedAtAsc(user.getId());
            if (memberships.isEmpty()) {
                throw ApiExceptions.forbidden("The account has no store membership");
            }
            if (memberships.size() > 1) {
                throw ApiExceptions.badRequest(
                        "STORE_SELECTION_REQUIRED", "storeId is required when an account belongs to multiple stores");
            }
            membership = memberships.getFirst();
        }
        return principal(user, membership);
    }

    @Transactional
    public AuthenticatedUser createStaff(UUID storeId, AuthModels.RegisterRequest request) {
        validatePassword(request.password());
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiExceptions.conflict("EMAIL_ALREADY_EXISTS", "An account already exists for this email");
        }
        if (!storeRepository.existsById(storeId)) {
            throw ApiExceptions.notFound("Store");
        }
        var now = Instant.now(clock);
        var user = new AppUser(
                UUID.randomUUID(), email, passwordEncoder.encode(request.password()), request.displayName().trim(), now);
        var membership = new StoreMembership(UUID.randomUUID(), storeId, user.getId(), StoreRole.STAFF, now);
        userRepository.save(user);
        membershipRepository.save(membership);
        return principal(user, membership);
    }

    private AuthenticatedUser principal(AppUser user, StoreMembership membership) {
        return new AuthenticatedUser(
                user.getId(), membership.getStoreId(), user.getEmail(), user.getDisplayName(), membership.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw ApiExceptions.badRequest("PASSWORD_TOO_LONG", "Password must be at most 72 UTF-8 bytes");
        }
    }

    private String normalizeCurrency(String value) {
        var currencyCode = value.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currencyCode);
            return currencyCode;
        } catch (IllegalArgumentException exception) {
            throw ApiExceptions.badRequest("INVALID_CURRENCY", "currencyCode must be an ISO 4217 code");
        }
    }

    private String validateTimeZone(String value) {
        try {
            return ZoneId.of(value.trim()).getId();
        } catch (ZoneRulesException exception) {
            throw ApiExceptions.badRequest("INVALID_TIME_ZONE", "timeZone must be a valid IANA zone ID");
        }
    }
}
