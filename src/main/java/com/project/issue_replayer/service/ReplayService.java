package com.project.issue_replayer.service;

import java.util.Map;

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
@RequiredArgsConstructor // Lombok: generates constructor to inject dependencies
@Slf4j                   // Lombok: gives us a logger
public class ReplayService {

    private final FailedApiRequestRepository repository;
    private final RestTemplate restTemplate;

    // Base URL of our own app (since we're replaying against ourselves)
    private static final String BASE_URL = "http://localhost:8080";

    /**
     * Replays a single failed request by its database ID.
     * 
     * @param id - the ID of the failed request in the database
     * @return a Map with the replay result details
     * 
     * STEP BY STEP:
     * 1. Find the failed request in DB
     * 2. Build the same HTTP request using RestTemplate
     * 3. Execute it
     * 4. Return success/failure result
     */
    public Map<String, Object> replayById(Long id) {

        // ---- STEP 1: Find the failed request in DB ----
        // findById returns Optional<FailedApiRequest>
        // orElseThrow = if not found, throw exception
        FailedApiRequest failedRequest = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("No failed request found with ID: " + id));

        log.info("Replaying failed request ID: {} | {} {}",
                id, failedRequest.getHttpMethod(), failedRequest.getEndpoint());

        // ---- STEP 2: Build the full URL ----
        // Example: "http://localhost:8080" + "/api/simulate/user/13"
        String fullUrl = BASE_URL + failedRequest.getEndpoint();

        // ---- STEP 3: Build HTTP headers ----
        // Headers tell the server what format the data is in
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);  // "I'm sending JSON"

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
