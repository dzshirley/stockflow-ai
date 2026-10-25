package com.stockflow.auth;

import com.stockflow.common.error.ApiExceptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final LoginAttemptGuard loginAttempts;
    private final boolean registrationEnabled;

    public AuthController(
            AuthenticationService authenticationService,
            SessionAuthenticationService sessionAuthenticationService,
            LoginAttemptGuard loginAttempts,
            @Value("${stockflow.security.registration-enabled:true}") boolean registrationEnabled) {
        this.authenticationService = authenticationService;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.loginAttempts = loginAttempts;
        this.registrationEnabled = registrationEnabled;
    }

    @GetMapping("/csrf")
    AuthModels.CsrfResponse csrf(CsrfToken token) {
        return new AuthModels.CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthModels.AuthResponse register(
            @Valid @RequestBody AuthModels.RegisterRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!registrationEnabled) {
            throw ApiExceptions.forbidden("Public registration is disabled");
        }
        var principal = authenticationService.register(body);
        sessionAuthenticationService.establish(principal, request, response);
        return AuthModels.AuthResponse.from(principal);
    }

    @PostMapping("/login")
    AuthModels.AuthResponse login(
            @Valid @RequestBody AuthModels.LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String remoteAddress = request.getRemoteAddr();
        loginAttempts.checkAllowed(body.email(), remoteAddress);
        AuthenticatedUser principal;
        try {
            principal = authenticationService.authenticate(body);
        } catch (com.stockflow.common.error.ApiException exception) {
            if (exception.status() == HttpStatus.UNAUTHORIZED) {
                loginAttempts.recordFailure(body.email(), remoteAddress);
            }
            throw exception;
        }
        loginAttempts.recordSuccess(body.email(), remoteAddress);
        sessionAuthenticationService.establish(principal, request, response);
        return AuthModels.AuthResponse.from(principal);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
                request, response, org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication());
    }

    @GetMapping("/me")
    AuthModels.AuthResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return AuthModels.AuthResponse.from(principal);
    }
}
