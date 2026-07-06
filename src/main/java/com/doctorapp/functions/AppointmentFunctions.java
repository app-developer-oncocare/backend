package com.doctorapp.functions;

import com.doctorapp.config.MongoConfig;
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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.jsonwebtoken.Claims;
import org.bson.Document;
import com.microsoft.azure.functions.annotation.BindingName;
import com.doctorapp.service.AppointmentService;
import com.doctorapp.model.Appointment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class AppointmentFunctions {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("getAppointments")
    public HttpResponseMessage getAppointments(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "appointments")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing get appointments request.");

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

        try {
            MongoDatabase db = MongoConfig.getDatabase();
            MongoCollection<Document> collection = db.getCollection("appointments");

            // 2. Query matching appointments
            List<Document> list = collection.find(Filters.eq("doctorId", doctorId))
                    .into(new ArrayList<>());

            // Map BSON Documents to clean DTO map to avoid ObjectId serialization issue
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (Document doc : list) {
                Map<String, Object> map = new HashMap<>(doc);
                if (doc.getObjectId("_id") != null) {
                    map.put("id", doc.getObjectId("_id").toHexString());
                    map.remove("_id");
                }
                resultList.add(map);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(resultList))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to fetch appointments", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to retrieve appointments: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }

    @FunctionName("updateAppointmentStatus")
    public HttpResponseMessage updateAppointmentStatus(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.PUT},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "appointments/{id}/status")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String appointmentId,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing update appointment status request.");

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

        // 2. Parse request body
        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            Map<String, String> bodyMap = MAPPER.readValue(body, Map.class);
            String status = bodyMap.get("status");
            String timeSlot = bodyMap.get("timeSlot");

            AppointmentService appointmentService = new AppointmentService();
            Appointment updatedAppt = appointmentService.updateAppointment(appointmentId, status, timeSlot);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(updatedAppt))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (IllegalArgumentException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"" + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to update appointment status", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to update appointment status: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }

    @FunctionName("createAppointment")
    public HttpResponseMessage createAppointment(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "appointments")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing create appointment request.");

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

        // 2. Parse request body
        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            Appointment appointment = MAPPER.readValue(body, Appointment.class);
            AppointmentService appointmentService = new AppointmentService();
            Appointment createdAppt = appointmentService.bookAppointment(appointment);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(createdAppt))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (IllegalArgumentException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"" + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to create appointment", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to create appointment: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
