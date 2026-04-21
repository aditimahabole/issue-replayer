package com.project.issue_replayer.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.project.issue_replayer.entity.FailedApiRequest;
import com.project.issue_replayer.repository.FailedApiRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REPLAY SERVICE
 * 
 * This class does the actual work of replaying a failed request.
 * 
 * HOW REPLAY WORKS:
 * 1. Look up the failed request from DB by ID
 * 2. Read its httpMethod, endpoint, and requestBody
 * 3. Use RestTemplate to make the SAME HTTP call again
 * 4. Check if it succeeded or failed again
 * 5. Mark it as replayed in the database
 * 
 * WHY a separate Service class?
 * - Controller = handles HTTP routing (thin layer)
 * - Service = handles business logic (thick layer)
 * - This separation is a Spring Boot best practice
 */
@Service                 // Tells Spring: "This is a business logic class, manage it for me"
@Slf4j                   // Lombok: gives us a logger
public class ReplayService {

    private final FailedApiRequestRepository repository;
    private final RestTemplate restTemplate;

    /**
     * @Value reads from application.properties at startup.
     * Instead of hardcoding "http://localhost:8080", we read it from config.
     * In production, you just change the property — no code changes needed!
     * 
     * ${app.replay.base-url} = reads property "app.replay.base-url"
     */
    @Value("${app.replay.base-url}")
    private String baseUrl;

    /**
     * Constructor injection (since we can't use @RequiredArgsConstructor with @Value)
     * Spring auto-injects repository and restTemplate.
     */
    public ReplayService(FailedApiRequestRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    /**
     * Maximum number of times a failed request can be replayed.
     * After this, we stop — the issue likely needs manual investigation.
     */
    private static final int MAX_REPLAY_ATTEMPTS = 3;

    /**
     * Replays a single failed request by its database ID.
     * 
     * @param id - the ID of the failed request in the database
     * @return a Map with the replay result details
     * 
     * STEP BY STEP:
     * 1. Find the failed request in DB
     * 2. Check if max retries exceeded
     * 3. Build the same HTTP request using RestTemplate
     * 4. Execute it
     * 5. Return success/failure result
     */
    public Map<String, Object> replayById(Long id) {

        // ---- STEP 1: Find the failed request in DB ----
        // findById returns Optional<FailedApiRequest>
        // orElseThrow = if not found, throw exception
        FailedApiRequest failedRequest = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("No failed request found with ID: " + id));

        log.info("Replaying failed request ID: {} | {} {}",
                id, failedRequest.getHttpMethod(), failedRequest.getEndpoint());

        // ---- STEP 1.5: Check retry limit ----
        // If we've already replayed this 3 times, stop trying
        if (failedRequest.getReplayCount() >= MAX_REPLAY_ATTEMPTS) {
            log.warn("Max replay attempts ({}) reached for ID: {}", MAX_REPLAY_ATTEMPTS, id);
            return Map.of(
                    "status", "MAX_RETRIES_EXCEEDED",
                    "failureId", id,
                    "replayCount", failedRequest.getReplayCount(),
                    "message", "This request has been replayed " + MAX_REPLAY_ATTEMPTS
                            + " times already. Manual investigation needed."
            );
        }

        // ---- STEP 2: Build the full URL ----
        // Example: "http://localhost:8080" + "/api/simulate/user/13"
        String fullUrl = baseUrl + failedRequest.getEndpoint();

        // ---- STEP 3: Build HTTP headers ----
        // Headers tell the server what format the data is in
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);  // "I'm sending JSON"
        headers.set("X-Replay", "true");  // Tell GlobalExceptionHandler: "Don't save this failure!"

        // ---- STEP 4: Build the request body ----
        // HttpEntity = combines headers + body into one object
        // For GET requests, body is null
        // For POST requests, body is the JSON string we saved earlier
        HttpEntity<String> httpEntity = new HttpEntity<>(
                failedRequest.getRequestBody(),  // body (can be null for GET)
                headers                           // headers
        );

        // ---- STEP 5: Make the HTTP call using RestTemplate ----
        try {
            // restTemplate.exchange() is the most flexible method:
            //   - fullUrl: WHERE to send the request
            //   - HttpMethod.valueOf("GET"): WHAT type of request
            //   - httpEntity: WHAT to send (headers + body)
            //   - String.class: WHAT type of response we expect
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.valueOf(failedRequest.getHttpMethod()),  // "GET" -> HttpMethod.GET
                    httpEntity,
                    String.class  // We expect a String response
            );

            // ---- STEP 6: SUCCESS! Mark as replayed ----
            failedRequest.setReplayed(true);
            failedRequest.setReplayCount(failedRequest.getReplayCount() + 1);
            repository.save(failedRequest);  // UPDATE in DB

            log.info("Replay SUCCESS for ID: {} | Status: {}", id, response.getStatusCode());

            return Map.of(
                    "status", "SUCCESS",
                    "failureId", id,
                    "replayedEndpoint", failedRequest.getEndpoint(),
                    "responseStatus", response.getStatusCode().value(),
                    "responseBody", response.getBody() != null ? response.getBody() : ""
            );

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            // ---- STEP 7: FAILED AGAIN (4xx or 5xx response) ----
            // HttpClientErrorException = 400-level errors (bad request, not found)
            // HttpServerErrorException = 500-level errors (server crash)
            log.warn("Replay FAILED AGAIN for ID: {} | Status: {}", id, ex.getStatusCode());

            // Increment replay count even on failure
            failedRequest.setReplayCount(failedRequest.getReplayCount() + 1);
            repository.save(failedRequest);

            return Map.of(
                    "status", "FAILED_AGAIN",
                    "failureId", id,
                    "replayedEndpoint", failedRequest.getEndpoint(),
                    "responseStatus", ex.getStatusCode().value(),
                    "errorMessage", ex.getResponseBodyAsString()
            );

        } catch (Exception ex) {
            // ---- STEP 8: UNEXPECTED ERROR (network down, etc.) ----
            log.error("Replay ERROR for ID: {} | {}", id, ex.getMessage());

            return Map.of(
                    "status", "ERROR",
                    "failureId", id,
                    "replayedEndpoint", failedRequest.getEndpoint(),
                    "errorMessage", ex.getMessage() != null ? ex.getMessage() : "Unknown error"
            );
        }
    }
}
