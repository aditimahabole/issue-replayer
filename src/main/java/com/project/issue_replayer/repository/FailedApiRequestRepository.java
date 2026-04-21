package com.project.issue_replayer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.issue_replayer.entity.FailedApiRequest;

/**
 * Repository for FailedApiRequest entity.
 * 
 * WHY do we need this?
 * JpaRepository gives us ready-made methods like:
 *   - save(entity)       ? INSERT into database
 *   - findAll()          ? SELECT * from table
 *   - findById(id)       ? SELECT by primary key
 *   - deleteById(id)     ? DELETE by primary key
 *   - count()            ? COUNT total rows
 * 
 * We just define the interface - Spring generates the implementation automatically!
 * We also add custom query methods below. Spring reads the method name and
 * auto-generates the SQL query. No SQL writing needed.
 */
@Repository
public interface FailedApiRequestRepository extends JpaRepository<FailedApiRequest, Long> {

    /**
     * Find all failed requests that have NOT been replayed yet.
     * Spring auto-generates: SELECT * FROM failed_api_request WHERE replayed = false
     */
    List<FailedApiRequest> findByReplayedFalse();

    /**
     * Find all failed requests that HAVE been replayed.
     * Spring auto-generates: SELECT * FROM failed_api_request WHERE replayed = true
     */
    List<FailedApiRequest> findByReplayedTrue();

    /**
     * Find all failed requests for a specific HTTP status code.
     * Example: findByStatusCode(500) ? all server errors
     * Spring auto-generates: SELECT * FROM failed_api_request WHERE status_code = ?
     */
    List<FailedApiRequest> findByStatusCode(int statusCode);

    /**
     * Find all failed requests for a specific endpoint.
     * Spring auto-generates: SELECT * FROM failed_api_request WHERE endpoint = ?
     */
    List<FailedApiRequest> findByEndpoint(String endpoint);
}

