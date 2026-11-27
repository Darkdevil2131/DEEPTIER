package com.deeptier.api;

import com.deeptier.api.dto.LoginRequest;
import com.deeptier.api.dto.LoginResponse;
import com.deeptier.api.dto.RegisterRequest;
import com.deeptier.api.dto.UserInfoResponse;
import com.deeptier.security.DemoUserStore;
import com.deeptier.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Stateless JWT authentication for DeepTier RBAC")
public class AuthController {

    private final DemoUserStore userStore;
    private final JwtService jwtService;

    @PostMapping("/register")
    @Operation(summary = "Register a new analyst user and obtain a JWT Bearer token")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        DemoUserStore.DemoUser user = userStore.register(req.username(), req.password());
        String token = jwtService.generateToken(user.username(), user.role());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LoginResponse(token, "Bearer", user.username(), user.role()));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain a JWT Bearer token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        DemoUserStore.DemoUser user = userStore.authenticate(req.username(), req.password())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        String token = jwtService.generateToken(user.username(), user.role());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", user.username(), user.role()));
    }

    @GetMapping("/me")
    @Operation(summary = "Return the currently authenticated user's username and role")
    public ResponseEntity<UserInfoResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_ANALYST");
        return ResponseEntity.ok(new UserInfoResponse(authentication.getName(), role));
    }
}
