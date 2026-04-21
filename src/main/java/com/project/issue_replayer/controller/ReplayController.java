package com.project.issue_replayer.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.issue_replayer.service.ReplayService;

import lombok.RequiredArgsConstructor;

/**
 * REST API to replay failed requests.
 * 
 * Usage:
 *   POST /api/replay/1    -> replays the failed request with ID 1
 *   POST /api/replay/2    -> replays the failed request with ID 2
 * 
 * WHY POST and not GET?
 *   Replay CHANGES state (marks request as replayed, may create data).
 *   GET should only READ data, never change it. That's a REST convention.
 */
@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;

    /**
     * POST /api/replay/{id}
     * 
     * Takes the ID of a failed request from the database,
     * and replays (re-executes) it against the original endpoint.
     * 
     * Example:
     *   Failed request #1 was: GET /api/simulate/user/13 (which threw 500)
     *   POST /api/replay/1 will call GET /api/simulate/user/13 again
     *   and tell you if it succeeded or failed again.
     */
    @PostMapping("/{id}")
    public ResponseEntity<Map<String, Object>> replayFailedRequest(@PathVariable Long id) {
        Map<String, Object> result = replayService.replayById(id);
        return ResponseEntity.ok(result);
    }
}
