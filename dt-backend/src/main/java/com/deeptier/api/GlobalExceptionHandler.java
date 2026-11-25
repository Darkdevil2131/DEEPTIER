package com.deeptier.api;

import com.deeptier.approval.CsvValidationException;
import com.deeptier.approval.IllegalStateTransitionException;
import com.deeptier.approval.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Modern RFC 7807 ProblemDetail Exception Handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://deeptier.io/errors/validation-error"));
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("retryable", false);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(CsvValidationException.class)
    public ResponseEntity<ProblemDetail> handleCsvValidation(CsvValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("CSV Validation Failed");
        problem.setType(URI.create("https://deeptier.io/errors/validation-error"));
        problem.setProperty("code", "CSV_VALIDATION_ERROR");
        problem.setProperty("errors", ex.getErrors());
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("retryable", false);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({IllegalStateTransitionException.class, IllegalStateException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("State Transition Conflict");
        problem.setType(URI.create("https://deeptier.io/errors/state-conflict"));
        problem.setProperty("code", "STATE_CONFLICT");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("retryable", false);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://deeptier.io/errors/not-found"));
        problem.setProperty("code", "NOT_FOUND");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("retryable", false);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArg(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://deeptier.io/errors/bad-request"));
        problem.setProperty("code", "BAD_REQUEST");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("retryable", false);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoHandlerFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://deeptier.io/errors/not-found"));
        problem.setProperty("code", "NOT_FOUND");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("retryable", false);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception: ", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://deeptier.io/errors/internal-error"));
        problem.setProperty("code", "INTERNAL_ERROR");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        problem.setProperty("retryable", true);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
