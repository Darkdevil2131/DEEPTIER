package com.deeptier.security;

import com.deeptier.approval.IllegalStateTransitionException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DemoUserStore {

    public record DemoUser(String username, String passwordHash, String role) {}

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    @Transactional
    public void seedDefaultUsers() {
        seedIfAbsent("analyst@deeptier.local", "Analyst@123", "ROLE_ANALYST");
        seedIfAbsent("approver@deeptier.local", "Approver@123", "ROLE_APPROVER");
        seedIfAbsent("admin@deeptier.local", "Admin@123", "ROLE_ADMIN");
    }

    private void seedIfAbsent(String username, String rawPassword, String role) {
        if (appUserRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            appUserRepository.save(AppUser.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .build());
        }
    }

    @Transactional
    public DemoUser register(String username, String rawPassword) {
        String normalized = username.trim().toLowerCase();
        if (appUserRepository.findByUsernameIgnoreCase(normalized).isPresent()) {
            throw new IllegalStateTransitionException("User already exists: " + normalized);
        }
        AppUser saved = appUserRepository.save(AppUser.builder()
                .username(normalized)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("ROLE_ANALYST")
                .build());
        return new DemoUser(saved.getUsername(), saved.getPasswordHash(), saved.getRole());
    }

    @Transactional(readOnly = true)
    public Optional<DemoUser> authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            return Optional.empty();
        }
        return appUserRepository.findByUsernameIgnoreCase(username.trim())
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .map(user -> new DemoUser(user.getUsername(), user.getPasswordHash(), user.getRole()));
    }
}
