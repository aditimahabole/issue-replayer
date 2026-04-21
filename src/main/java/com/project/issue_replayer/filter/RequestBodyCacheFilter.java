package com.project.issue_replayer.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REQUEST BODY CACHING FILTER
 * 
 * PROBLEM:
 *   In a Servlet, the request body (InputStream) can only be read ONCE.
 *   Spring reads it to parse @RequestBody, so by the time our
 *   GlobalExceptionHandler runs, the body is already gone (null).
 * 
 * SOLUTION:
 *   This filter wraps every request in a ContentCachingRequestWrapper.
 *   This wrapper reads the body and stores a COPY in memory.
 *   Now the body can be read multiple times.
 * 
 * HOW FILTERS WORK:
 *   Filters run BEFORE any controller. They sit in a chain:
 *   Request -> Filter1 -> Filter2 -> Controller -> Response
 * 
 *   OncePerRequestFilter = ensures this filter runs only ONCE per request
 *   (not multiple times if request is forwarded internally)
 * 
 * @Order(Ordered.HIGHEST_PRECEDENCE) = run this filter FIRST,
 *   before any other filter, so the body is cached from the start.
 */
@Component  // Spring: "auto-detect and register this filter"
@Order(Ordered.HIGHEST_PRECEDENCE)  // Run FIRST in the filter chain
public class RequestBodyCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,      // the original request
            HttpServletResponse response,    // the response
            FilterChain filterChain)         // the next filter/controller in the chain
            throws ServletException, IOException {

        // Wrap the request so the body can be read multiple times
        // ContentCachingRequestWrapper = Spring's built-in caching wrapper
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request);

        // Continue the chain with the WRAPPED request (not the original)
        // This means all subsequent filters and controllers will use
        // the cached version, and we can read the body later in the exception handler
        filterChain.doFilter(wrappedRequest, response);
    }
}
