package com.deeptier.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class DemoUserStore {

    public record DemoUser(String username, String passwordHash, String role) {}

    private final Map<String, DemoUser> users;
    private final PasswordEncoder passwordEncoder;

    public DemoUserStore(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.users = Map.of(
                "analyst@deeptier.local",
                new DemoUser("analyst@deeptier.local", passwordEncoder.encode("Analyst@123"), "ROLE_ANALYST"),
                "approver@deeptier.local",
                new DemoUser("approver@deeptier.local", passwordEncoder.encode("Approver@123"), "ROLE_APPROVER"),
                "admin@deeptier.local",
                new DemoUser("admin@deeptier.local", passwordEncoder.encode("Admin@123"), "ROLE_ADMIN")
        );
    }

    public Optional<DemoUser> authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            return Optional.empty();
        }
        DemoUser user = users.get(username.trim().toLowerCase());
        if (user == null || !passwordEncoder.matches(rawPassword, user.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
