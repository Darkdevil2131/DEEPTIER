package com.deeptier.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findExisting(String idempotencyKey, String endpoint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKeyAndEndpoint(idempotencyKey, endpoint);
    }

    @Transactional
    public void record(String idempotencyKey, String endpoint, int httpStatus, Object response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        try {
            String json = response instanceof String str ? str : objectMapper.writeValueAsString(response);
            if (json != null && json.length() > 4000) {
                json = json.substring(0, 4000);
            }

            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .endpoint(endpoint)
                    .httpStatus(httpStatus)
                    .responseBody(json)
                    .createdAt(Instant.now())
                    .build();

            repository.save(record);
            log.info("Idempotency record saved: key={}, endpoint={}, status={}", idempotencyKey, endpoint, httpStatus);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idempotency response body: {}", e.getMessage());
        }
    }

    public <T> T deserialize(IdempotencyRecord record, Class<T> targetClass) {
        try {
            return objectMapper.readValue(record.getResponseBody(), targetClass);
        } catch (Exception e) {
            log.error("Failed to deserialize cached response body: {}", e.getMessage());
            throw new IllegalStateException("Failed to deserialize idempotency response", e);
        }
    }
}
