package com.deeptier.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {

    public record JwtClaims(String username, String role) {}

    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secretBytes;
    private final long expirationSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${deeptier.auth.jwt-secret:deeptier-dev-jwt-secret-change-in-prod-2026-min-32-bytes}") String secret,
            @Value("${deeptier.auth.expiration-seconds:86400}") long expirationSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String username, String role) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", username);
            payload.put("role", role);
            payload.put("iat", now);
            payload.put("exp", now + expirationSeconds);

            String headerB64 = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(header));
            String payloadB64 = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerB64 + "." + payloadB64;
            String sigB64 = URL_ENCODER.encodeToString(hmacSha256(signingInput));
            return signingInput + "." + sigB64;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate JWT", ex);
        }
    }

    public Optional<JwtClaims> parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSig = hmacSha256(signingInput);
            byte[] actualSig = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return Optional.empty();
            }

            JsonNode claims = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            long exp = claims.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            String sub = claims.path("sub").asText("").trim();
            String role = claims.path("role").asText("").trim();
            if (sub.isEmpty() || role.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new JwtClaims(sub, role));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private byte[] hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(secretBytes, HMAC_ALG));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
