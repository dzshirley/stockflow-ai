package com.stockflow.store;

import com.stockflow.auth.AuthModels;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreService storeService;
    private final StoreContextProvider contextProvider;

    public StoreController(StoreService storeService, StoreContextProvider contextProvider) {
        this.storeService = storeService;
        this.contextProvider = contextProvider;
    }

    @GetMapping
    StoreModels.StoreResponse current() {
        return storeService.current(contextProvider.current());
    }

    @GetMapping("/members")
    @PreAuthorize("hasRole('ADMIN')")
    StoreModels.MembersResponse members() {
        return storeService.members(contextProvider.current());
    }

    @PostMapping("/members")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    AuthModels.AuthResponse createStaff(@Valid @RequestBody StoreModels.CreateStaffRequest request) {
        return storeService.createStaff(contextProvider.current(), request);
    }
}
