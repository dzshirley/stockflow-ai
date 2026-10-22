package com.stockflow.store;

import com.stockflow.auth.AppUserRepository;
import com.stockflow.auth.AuthModels;
import com.stockflow.auth.AuthenticationService;
import com.stockflow.common.error.ApiExceptions;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreMembershipRepository membershipRepository;
    private final AppUserRepository userRepository;
    private final AuthenticationService authenticationService;

    public StoreService(
            StoreRepository storeRepository,
            StoreMembershipRepository membershipRepository,
            AppUserRepository userRepository,
            AuthenticationService authenticationService) {
        this.storeRepository = storeRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.authenticationService = authenticationService;
    }

    @Transactional(readOnly = true)
    public StoreModels.StoreResponse current(StoreContext context) {
        var store = requireStore(context.storeId());
        return new StoreModels.StoreResponse(
                store.getId(), store.getName(), store.getCurrencyCode(), store.getTimeZone(), context.role());
    }

    @Transactional(readOnly = true)
    public StoreModels.MembersResponse members(StoreContext context) {
        var members = membershipRepository.findByStoreIdOrderByCreatedAtAsc(context.storeId()).stream()
                .map(membership -> {
                    var user = userRepository.findById(membership.getUserId())
                            .orElseThrow(() -> ApiExceptions.notFound("User"));
                    return new StoreModels.MemberResponse(
                            user.getId(), user.getEmail(), user.getDisplayName(), membership.getRole());
                })
                .toList();
        return new StoreModels.MembersResponse(members);
    }

    @Transactional
    public AuthModels.AuthResponse createStaff(StoreContext context, StoreModels.CreateStaffRequest request) {
        var store = requireStore(context.storeId());
        var principal = authenticationService.createStaff(context.storeId(), request.asRegistration(store));
        return AuthModels.AuthResponse.from(principal);
    }

    private Store requireStore(UUID storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> ApiExceptions.notFound("Store"));
    }
}
