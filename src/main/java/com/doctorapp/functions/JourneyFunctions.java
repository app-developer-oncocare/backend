package com.doctorapp.functions;

import com.doctorapp.config.MongoConfig;
import com.doctorapp.model.Medicine;
import com.doctorapp.model.Goal;
import com.doctorapp.model.DiagnosisLog;
import com.doctorapp.model.HomeServiceRequest;
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
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.jsonwebtoken.Claims;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.logging.Level;

public class JourneyFunctions {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MEDICINES_COLLECTION = "medicines";
    private static final String GOALS_COLLECTION = "goals";
    private static final String DIAGNOSIS_COLLECTION = "diagnosis_logs";
    private static final String SERVICES_COLLECTION = "support_services";

    private MongoCollection<Document> getMedicinesCollection() {
        return MongoConfig.getDatabase().getCollection(MEDICINES_COLLECTION);
    }

    private MongoCollection<Document> getGoalsCollection() {
        return MongoConfig.getDatabase().getCollection(GOALS_COLLECTION);
    }

    private MongoCollection<Document> getDiagnosisCollection() {
        return MongoConfig.getDatabase().getCollection(DIAGNOSIS_COLLECTION);
    }

    private MongoCollection<Document> getServicesCollection() {
        return MongoConfig.getDatabase().getCollection(SERVICES_COLLECTION);
    }

    @FunctionName("getMedicines")
    public HttpResponseMessage getMedicines(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/medicine")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Fetching patient medicines.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);

        try {
            MongoCollection<Document> collection = getMedicinesCollection();
            List<Medicine> medicines = new ArrayList<>();

            for (Document doc : collection.find(Filters.eq("patientId", patientId))) {
                medicines.add(mapDocToMedicine(doc));
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(medicines))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to retrieve medicines", e);
            return errorResponse(request, "Failed to retrieve medicines: " + e.getMessage());
        }
    }

    @FunctionName("saveMedicine")
    public HttpResponseMessage saveMedicine(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/medicine")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Saving medicine or logging compliance.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);
        String body = request.getBody().orElse("");

        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .build();
        }

        try {
            Map<String, Object> bodyMap = MAPPER.readValue(body, Map.class);
            MongoCollection<Document> collection = getMedicinesCollection();

            // Check if this is a compliance checkmark update
            if (bodyMap.containsKey("medicineId") && bodyMap.containsKey("date")) {
                String medicineId = (String) bodyMap.get("medicineId");
                String date = (String) bodyMap.get("date");
                Boolean checked = (Boolean) bodyMap.get("checked");

                if (!ObjectId.isValid(medicineId)) {
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                            .body("Invalid medicineId format.")
                            .build();
                }

                collection.updateOne(
                        Filters.eq("_id", new ObjectId(medicineId)),
                        Updates.set("compliance." + date, checked)
                );

                Document updated = collection.find(Filters.eq("_id", new ObjectId(medicineId))).first();
                return request.createResponseBuilder(HttpStatus.OK)
                        .body(MAPPER.writeValueAsString(mapDocToMedicine(updated)))
                        .header("Content-Type", "application/json")
                        .build();
            }

            // Otherwise, register a new medicine prescription log
            String name = (String) bodyMap.get("name");
            String dosage = (String) bodyMap.get("dosage");
            String scheduleSlot = (String) bodyMap.get("scheduleSlot");
            String startDate = (String) bodyMap.get("startDate");
            String endDate = (String) bodyMap.get("endDate");

            if (name == null || name.isEmpty() || scheduleSlot == null || scheduleSlot.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("name and scheduleSlot are required fields.")
                        .build();
            }

            Document doc = new Document()
                    .append("patientId", patientId)
                    .append("name", name)
                    .append("dosage", dosage != null ? dosage : "")
                    .append("scheduleSlot", scheduleSlot.toLowerCase())
                    .append("startDate", startDate != null ? startDate : "")
                    .append("endDate", endDate != null ? endDate : "")
                    .append("compliance", new Document());

            collection.insertOne(doc);
            Medicine medicine = mapDocToMedicine(doc);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(medicine))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to save medicine log", e);
            return errorResponse(request, "Failed to save medicine log: " + e.getMessage());
        }
    }

    @FunctionName("getGoals")
    public HttpResponseMessage getGoals(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/goals")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Fetching patient weekly goals.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);

        try {
            MongoCollection<Document> collection = getGoalsCollection();
            Document doc = collection.find(Filters.eq("patientId", patientId)).first();
            
            Goal goal;
            if (doc == null) {
                // Initialize default goal parameters if not present in DB
                goal = new Goal();
                goal.setPatientId(patientId);
                goal.setWaterTarget(8);
                goal.setWaterCurrent(2);
                goal.setStepsTarget(10000);
                goal.setStepsCurrent(3000);
                goal.setSleepTarget(8);
                goal.setSleepCurrent(6);
                goal.setWeekStartDate("2026-07-05");
            } else {
                goal = mapDocToGoal(doc);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(goal))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to retrieve goals", e);
            return errorResponse(request, "Failed to retrieve goals: " + e.getMessage());
        }
    }

    @FunctionName("saveGoals")
    public HttpResponseMessage saveGoals(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/goals")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Saving patient goals metrics.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);
        String body = request.getBody().orElse("");

        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .build();
        }

        try {
            Goal input = MAPPER.readValue(body, Goal.class);
            MongoCollection<Document> collection = getGoalsCollection();

            Document existing = collection.find(Filters.eq("patientId", patientId)).first();
            
            Document doc = new Document()
                    .append("patientId", patientId)
                    .append("waterTarget", input.getWaterTarget() != null ? input.getWaterTarget() : 8)
                    .append("waterCurrent", input.getWaterCurrent() != null ? input.getWaterCurrent() : 0)
                    .append("stepsTarget", input.getStepsTarget() != null ? input.getStepsTarget() : 10000)
                    .append("stepsCurrent", input.getStepsCurrent() != null ? input.getStepsCurrent() : 0)
                    .append("sleepTarget", input.getSleepTarget() != null ? input.getSleepTarget() : 8)
                    .append("sleepCurrent", input.getSleepCurrent() != null ? input.getSleepCurrent() : 0)
                    .append("weekStartDate", input.getWeekStartDate() != null ? input.getWeekStartDate() : "2026-07-05");

            if (existing == null) {
                collection.insertOne(doc);
            } else {
                collection.updateOne(Filters.eq("patientId", patientId), new Document("$set", doc));
            }

            Document updated = collection.find(Filters.eq("patientId", patientId)).first();
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(mapDocToGoal(updated)))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to save goals", e);
            return errorResponse(request, "Failed to save goals: " + e.getMessage());
        }
    }

    // Helper functions
    private Claims getClaims(HttpRequestMessage<Optional<String>> request) {
        String authHeader = request.getHeaders().get("authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return JwtUtil.parseToken(token);
    }

    private HttpResponseMessage unauthorizedResponse(HttpRequestMessage<Optional<String>> request) {
        return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Missing, invalid, or expired Authorization token.\"}")
                .header("Content-Type", "application/json")
                .build();
    }

    private HttpResponseMessage errorResponse(HttpRequestMessage<Optional<String>> request, String errorMsg) {
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + errorMsg + "\"}")
                .header("Content-Type", "application/json")
                .build();
    }

    private Medicine mapDocToMedicine(Document doc) {
        Medicine med = new Medicine();
        med.setId(doc.getObjectId("_id").toHexString());
        med.setPatientId(doc.getString("patientId"));
        med.setName(doc.getString("name"));
        med.setDosage(doc.getString("dosage"));
        med.setScheduleSlot(doc.getString("scheduleSlot"));
        med.setStartDate(doc.getString("startDate"));
        med.setEndDate(doc.getString("endDate"));

        Document compDoc = (Document) doc.get("compliance");
        Map<String, Boolean> compliance = new HashMap<>();
        if (compDoc != null) {
            for (String key : compDoc.keySet()) {
                compliance.put(key, compDoc.getBoolean(key));
            }
        }
        med.setCompliance(compliance);
        return med;
    }

    private Goal mapDocToGoal(Document doc) {
        Goal goal = new Goal();
        goal.setId(doc.getObjectId("_id").toHexString());
        goal.setPatientId(doc.getString("patientId"));
        goal.setWaterTarget(doc.getInteger("waterTarget"));
        goal.setWaterCurrent(doc.getInteger("waterCurrent"));
        goal.setStepsTarget(doc.getInteger("stepsTarget"));
        goal.setStepsCurrent(doc.getInteger("stepsCurrent"));
        goal.setSleepTarget(doc.getInteger("sleepTarget"));
        goal.setSleepCurrent(doc.getInteger("sleepCurrent"));
        goal.setWeekStartDate(doc.getString("weekStartDate"));
        return goal;
    }

    @FunctionName("getDiagnosis")
    public HttpResponseMessage getDiagnosis(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/diagnosis")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Fetching patient diagnosis logs.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);

        try {
            MongoCollection<Document> collection = getDiagnosisCollection();
            List<DiagnosisLog> logs = new ArrayList<>();

            for (Document doc : collection.find(Filters.eq("patientId", patientId))) {
                logs.add(mapDocToDiagnosisLog(doc));
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(logs))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to retrieve diagnosis logs", e);
            return errorResponse(request, "Failed to retrieve diagnosis logs: " + e.getMessage());
        }
    }

    @FunctionName("saveDiagnosis")
    public HttpResponseMessage saveDiagnosis(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/diagnosis")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Saving diagnosis log.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);
        String body = request.getBody().orElse("");

        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .build();
        }

        try {
            DiagnosisLog input = MAPPER.readValue(body, DiagnosisLog.class);
            MongoCollection<Document> collection = getDiagnosisCollection();

            Document doc = new Document()
                    .append("patientId", patientId)
                    .append("symptomsSummary", input.getSymptomsSummary() != null ? input.getSymptomsSummary() : "")
                    .append("duration", input.getDuration() != null ? input.getDuration() : "")
                    .append("pastHistory", input.getPastHistory() != null ? input.getPastHistory() : "")
                    .append("matchedSpecialty", input.getMatchedSpecialty() != null ? input.getMatchedSpecialty() : "")
                    .append("createdAt", new Date().toString());

            collection.insertOne(doc);
            DiagnosisLog saved = mapDocToDiagnosisLog(doc);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(saved))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to save diagnosis log", e);
            return errorResponse(request, "Failed to save diagnosis log: " + e.getMessage());
        }
    }

    private DiagnosisLog mapDocToDiagnosisLog(Document doc) {
        DiagnosisLog log = new DiagnosisLog();
        log.setId(doc.getObjectId("_id").toHexString());
        log.setPatientId(doc.getString("patientId"));
        log.setSymptomsSummary(doc.getString("symptomsSummary"));
        log.setDuration(doc.getString("duration"));
        log.setPastHistory(doc.getString("pastHistory"));
        log.setMatchedSpecialty(doc.getString("matchedSpecialty"));
        log.setCreatedAt(doc.getString("createdAt"));
        return log;
    }

    @FunctionName("getServiceRequests")
    public HttpResponseMessage getServiceRequests(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/services")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Fetching patient support services logs.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);

        try {
            MongoCollection<Document> collection = getServicesCollection();
            List<HomeServiceRequest> logs = new ArrayList<>();

            for (Document doc : collection.find(Filters.eq("patientId", patientId))) {
                logs.add(mapDocToHomeServiceRequest(doc));
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(logs))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to retrieve support services logs", e);
            return errorResponse(request, "Failed to retrieve support services logs: " + e.getMessage());
        }
    }

    @FunctionName("saveServiceRequest")
    public HttpResponseMessage saveServiceRequest(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "journey/services")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Saving support service request.");

        Claims claims = getClaims(request);
        if (claims == null) {
            return unauthorizedResponse(request);
        }

        String patientId = claims.get("userId", String.class);
        String body = request.getBody().orElse("");

        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .build();
        }

        try {
            HomeServiceRequest input = MAPPER.readValue(body, HomeServiceRequest.class);
            MongoCollection<Document> collection = getServicesCollection();

            Document doc = new Document()
                    .append("patientId", patientId)
                    .append("serviceType", input.getServiceType() != null ? input.getServiceType() : "")
                    .append("scheduledDate", input.getScheduledDate() != null ? input.getScheduledDate() : "")
                    .append("address", input.getAddress() != null ? input.getAddress() : "")
                    .append("specialNotes", input.getSpecialNotes() != null ? input.getSpecialNotes() : "")
                    .append("status", "RECEIVED")
                    .append("createdAt", new Date().toString());

            collection.insertOne(doc);
            HomeServiceRequest saved = mapDocToHomeServiceRequest(doc);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(saved))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to save support service request", e);
            return errorResponse(request, "Failed to save support service request: " + e.getMessage());
        }
    }

    private HomeServiceRequest mapDocToHomeServiceRequest(Document doc) {
        HomeServiceRequest log = new HomeServiceRequest();
        log.setId(doc.getObjectId("_id").toHexString());
        log.setPatientId(doc.getString("patientId"));
        log.setServiceType(doc.getString("serviceType"));
        log.setScheduledDate(doc.getString("scheduledDate"));
        log.setAddress(doc.getString("address"));
        log.setSpecialNotes(doc.getString("specialNotes"));
        log.setStatus(doc.getString("status"));
        log.setCreatedAt(doc.getString("createdAt"));
        return log;
    }
}
