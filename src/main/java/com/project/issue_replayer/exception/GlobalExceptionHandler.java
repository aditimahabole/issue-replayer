package com.project.issue_replayer.exception;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.project.issue_replayer.entity.FailedApiRequest;
import com.project.issue_replayer.repository.FailedApiRequestRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GLOBAL EXCEPTION HANDLER
 * 
 * HOW IT WORKS:
 * ?????????????
 * 1. Any controller throws an exception (e.g., RuntimeException)
 * 2. Spring catches it BEFORE sending response to client
 * 3. This class intercepts it using @ExceptionHandler
 * 4. We SAVE the failure details to the database
 * 5. We return a clean error response to the client
 * 
 * WHY?
 * ????
 * Without this, exceptions would just show ugly stack traces.
 * With this, every failure is:
 *   ? Logged to console
 *   ? Saved to database (for replay later)
 *   ? Returned as clean JSON to the client
 * 
 * This is AUTOMATIC - you don't need to add try-catch in every controller!
 * Any new controller you create will also be covered.
 */
@RestControllerAdvice   // Applies to ALL controllers globally
@RequiredArgsConstructor // Lombok: auto-generates constructor for final fields
@Slf4j                  // Lombok: auto-generates a logger called "log"
public class GlobalExceptionHandler {

    private final FailedApiRequestRepository repository;

    /**
     * Catches any RuntimeException thrown by any controller.
     * 
     * FLOW:
     * Controller throws RuntimeException
     *   ? Spring intercepts it
     *   ? This method runs
     *   ? Saves failure to DB
     *   ? Returns 500 error JSON to client
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {

        // 1. Log the error
        log.error("? Exception caught at {} {} ? {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());

        // 2. Save to database
        FailedApiRequest failedRequest = FailedApiRequest.builder()
                .httpMethod(request.getMethod())
                .endpoint(request.getRequestURI())
                .requestBody(getRequestBody(request))
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())  // 500
                .errorMessage(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        FailedApiRequest saved = repository.save(failedRequest);
        log.info("? Failure saved to DB with ID: {}", saved.getId());

        // 3. Return clean error response to client
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal Server Error",
                        "message", ex.getMessage(),
                        "failureId", saved.getId(),
                        "path", request.getRequestURI(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    /**
     * Catches any generic Exception (fallback for unexpected errors).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("? Unexpected exception at {} {} ? {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());

        FailedApiRequest failedRequest = FailedApiRequest.builder()
                .httpMethod(request.getMethod())
                .endpoint(request.getRequestURI())
                .requestBody(getRequestBody(request))
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        FailedApiRequest saved = repository.save(failedRequest);
        log.info("? Failure saved to DB with ID: {}", saved.getId());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Unexpected Error",
                        "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error",
                        "failureId", saved.getId(),
                        "path", request.getRequestURI(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    /**
     * Helper: Try to read the request body.
     * Note: Request body can only be read once in a servlet,
     * so this may return null for POST requests.
     * We'll improve this later with a filter.
     */
    private String getRequestBody(HttpServletRequest request) {
        // For GET requests, capture query string instead
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return request.getQueryString();
        }
        return null; // We'll capture POST body via filter in next step
    }
}

