package com.doctorapp.functions;

import com.doctorapp.model.User;
import com.doctorapp.service.DoctorService;
import com.doctorapp.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import io.jsonwebtoken.Claims;

import java.util.Optional;
import java.util.List;
import java.util.logging.Level;

public class DoctorFunctions {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DoctorService doctorService = new DoctorService();

    @FunctionName("getDoctors")
    public HttpResponseMessage getDoctors(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "doctors")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing get doctors request.");

        // 1. Authenticate Request via JWT Bearer Token
        String authHeader = request.getHeaders().get("authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Missing or invalid Authorization header.\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }

        String token = authHeader.substring(7);
        if (!JwtUtil.validateToken(token)) {
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Token is expired or invalid.\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            List<User> list = doctorService.getAllDoctors();
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(list))
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to fetch doctors list", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to retrieve doctors list: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }

    @FunctionName("updateProfile")
    public HttpResponseMessage updateProfile(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.PUT},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "doctor/profile")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing update profile request.");

        // 1. Authenticate Request via JWT Bearer Token
        String authHeader = request.getHeaders().get("authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Missing or invalid Authorization header.\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }

        String token = authHeader.substring(7);
        if (!JwtUtil.validateToken(token)) {
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Token is expired or invalid.\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }

        Claims claims = JwtUtil.parseToken(token);
        String doctorId = (String) claims.get("userId");

        // 2. Parse request body
        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            // Deserialize body to User model
            User updatedUser = MAPPER.readValue(body, User.class);
            
            // Execute update
            User savedUser = doctorService.updateProfile(doctorId, updatedUser);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(savedUser))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (IllegalArgumentException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"" + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to update profile", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to update profile: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
