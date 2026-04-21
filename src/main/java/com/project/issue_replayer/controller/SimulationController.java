package com.project.issue_replayer.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A dummy API that simulates real-world failures.
 * 
 * WHY? Our Issue Replayer system needs failed requests to capture.
 * These endpoints intentionally fail for certain inputs so we can
 * test our failure-logging and replay pipeline.
 */
@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    /**
     * GET /api/simulate/user/{id}
     * 
     * Simulates fetching a user by ID.
     * - ID 1-100   ? SUCCESS (returns user data)
     * - ID 0       ? 400 Bad Request (invalid input)
     * - ID > 100   ? 404 Not Found (user doesn't exist)
     * - ID 13      ? 500 Internal Server Error (simulated bug!)
     */
    @GetMapping("/user/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable int id) {

        // Simulate: Invalid input
        if (id <= 0) {
            return ResponseEntity
                    .badRequest()  // 400
                    .body(Map.of(
                            "error", "Invalid user ID",
                            "message", "User ID must be a positive number",
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }

        // Simulate: Unexpected server crash (like a NullPointerException in production)
        if (id == 13) {
            throw new RuntimeException("Simulated server crash! Database connection lost.");
        }

        // Simulate: User not found
        if (id > 100) {
            return ResponseEntity
                    .status(404)  // 404
                    .body(Map.of(
                            "error", "User not found",
                            "message", "No user exists with ID: " + id,
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }

        // Success: Return fake user data
        return ResponseEntity.ok(Map.of(
                "id", id,
                "name", "User_" + id,
                "email", "user" + id + "@example.com",
                "status", "active"
        ));
    }

    /**
     * POST /api/simulate/order
     * 
     * Simulates creating an order.
     * - Valid body with "product" and "quantity" ? SUCCESS
     * - Missing "product" field               ? 400 Bad Request
     * - Quantity > 1000                        ? 500 Server Error (inventory crash)
     * 
     * Example valid body:
     * {
     *   "product": "Laptop",
     *   "quantity": 2
     * }
     */
    @PostMapping("/order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {

        String product = (String) body.get("product");
        Object quantityObj = body.get("quantity");

        // Simulate: Missing required field
        if (product == null || product.isBlank()) {
            return ResponseEntity
                    .badRequest()  // 400
                    .body(Map.of(
                            "error", "Validation failed",
                            "message", "Field 'product' is required and cannot be empty",
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }

        // Simulate: Missing quantity
        if (quantityObj == null) {
            return ResponseEntity
                    .badRequest()  // 400
                    .body(Map.of(
                            "error", "Validation failed",
                            "message", "Field 'quantity' is required",
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }

        int quantity = (int) quantityObj;

        // Simulate: Inventory system crash for large orders
        if (quantity > 1000) {
            throw new RuntimeException("Simulated crash! Inventory service timeout for bulk order.");
        }

        // Success: Return order confirmation
        return ResponseEntity.ok(Map.of(
                "orderId", "ORD-" + System.currentTimeMillis(),
                "product", product,
                "quantity", quantity,
                "status", "confirmed",
                "message", "Order placed successfully!"
        ));
    }
}
