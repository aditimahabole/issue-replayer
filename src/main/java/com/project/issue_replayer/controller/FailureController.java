package com.project.issue_replayer.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.issue_replayer.entity.FailedApiRequest;
import com.project.issue_replayer.repository.FailedApiRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * REST API to view captured failures.
 * 
Think of this as your "failure dashboard" - 
 * you can see all failed requests that were automatically saved.
 */
@RestController
@RequestMapping("/api/failures")
@RequiredArgsConstructor
public class FailureController {

    private final FailedApiRequestRepository repository;

    /**
     * GET /api/failures
     * Returns ALL saved failures.
     */
    @GetMapping
    public ResponseEntity<List<FailedApiRequest>> getAllFailures() {
        return ResponseEntity.ok(repository.findAll());
    }

    /**
     * GET /api/failures/pending
     * Returns only failures that have NOT been replayed yet.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<FailedApiRequest>> getPendingFailures() {
        return ResponseEntity.ok(repository.findByReplayedFalse());
    }

    /**
     * GET /api/failures/replayed
     * Returns only failures that HAVE been replayed.
     */
    @GetMapping("/replayed")
    public ResponseEntity<List<FailedApiRequest>> getReplayedFailures() {
        return ResponseEntity.ok(repository.findByReplayedTrue());
    }

    /**
     * GET /api/failures/by-status?code=500
     * Returns failures filtered by HTTP status code.
     */
    @GetMapping("/by-status")
    public ResponseEntity<List<FailedApiRequest>> getByStatusCode(@RequestParam int code) {
        return ResponseEntity.ok(repository.findByStatusCode(code));
    }

    /**
     * GET /api/failures/count
     * Returns total number of captured failures.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> getFailureCount() {
        return ResponseEntity.ok(repository.count());
    }
}

