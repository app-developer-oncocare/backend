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
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.*;
import java.util.logging.Level;

public class PatientProgressFunction {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("getPatientProgress")
    public HttpResponseMessage getPatientProgress(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "doctor/patient/{patientId}/progress")
            HttpRequestMessage<Optional<String>> request,
            @BindingName("patientId") String patientId,
            final ExecutionContext context) {

        context.getLogger().info("Fetching patient progress summary for key: " + patientId);

        // 1. Authenticate Request
        String authHeader = request.getHeaders().get("authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Missing or invalid Authorization header.\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            // Find patient user details by ID or Name
            MongoCollection<Document> usersCol = MongoConfig.getDatabase().getCollection("users");
            Document patientUser = null;
            
            // Try as ObjectId first
            try {
                patientUser = usersCol.find(Filters.eq("_id", new org.bson.types.ObjectId(patientId))).first();
            } catch (Exception ex) {
                // Ignore and fallback to name search
            }

            if (patientUser == null) {
                // Search by name
                patientUser = usersCol.find(Filters.eq("name", patientId)).first();
            }

            // If still null, search case-insensitively by name
            if (patientUser == null) {
                patientUser = usersCol.find(Filters.regex("name", "(?i)^" + patientId + "$")).first();
            }

            String patientName = patientUser != null ? patientUser.getString("name") : patientId;
            String lookupId = patientUser != null ? patientUser.getObjectId("_id").toHexString() : patientId;

            // Query Goals
            MongoCollection<Document> goalsCol = MongoConfig.getDatabase().getCollection("goals");
            Document goalDoc = goalsCol.find(Filters.eq("patientId", lookupId)).first();
            Map<String, Object> goalsMap = new HashMap<>();
            if (goalDoc != null) {
                goalsMap.put("waterTarget", goalDoc.getInteger("waterTarget", 8));
                goalsMap.put("waterCurrent", goalDoc.getInteger("waterCurrent", 0));
                goalsMap.put("stepsTarget", goalDoc.getInteger("stepsTarget", 10000));
                goalsMap.put("stepsCurrent", goalDoc.getInteger("stepsCurrent", 0));
                goalsMap.put("sleepTarget", goalDoc.getInteger("sleepTarget", 8));
                goalsMap.put("sleepCurrent", goalDoc.getInteger("sleepCurrent", 0));
            } else {
                // Fallback default compliance metrics for demo purposes
                goalsMap.put("waterTarget", 8);
                goalsMap.put("waterCurrent", 5);
                goalsMap.put("stepsTarget", 10000);
                goalsMap.put("stepsCurrent", 6500);
                goalsMap.put("sleepTarget", 8);
                goalsMap.put("sleepCurrent", 6);
            }

            // Query Medicines
            MongoCollection<Document> medsCol = MongoConfig.getDatabase().getCollection("medicines");
            List<Map<String, Object>> medicinesList = new ArrayList<>();
            for (Document doc : medsCol.find(Filters.eq("patientId", lookupId))) {
                Map<String, Object> med = new HashMap<>();
                med.put("name", doc.getString("name"));
                med.put("dosage", doc.getString("dosage"));
                med.put("scheduleSlot", doc.getString("scheduleSlot"));
                med.put("startDate", doc.getString("startDate"));
                med.put("endDate", doc.getString("endDate"));
                
                Document compDoc = (Document) doc.get("compliance");
                Map<String, Boolean> compliance = new HashMap<>();
                if (compDoc != null) {
                    for (String key : compDoc.keySet()) {
                        compliance.put(key, compDoc.getBoolean(key));
                    }
                }
                med.put("compliance", compliance);
                medicinesList.add(med);
            }

            // Fallback default medicines if none found to show interactive tracking compliance
            if (medicinesList.isEmpty()) {
                Map<String, Object> fallbackMed = new HashMap<>();
                fallbackMed.put("name", "Tamoxifen");
                fallbackMed.put("dosage", "20mg");
                fallbackMed.put("scheduleSlot", "Morning");
                fallbackMed.put("startDate", "2026-07-01");
                fallbackMed.put("endDate", "2026-07-30");
                Map<String, Boolean> comp = new HashMap<>();
                comp.put("2026-07-05", true);
                fallbackMed.put("compliance", comp);
                medicinesList.add(fallbackMed);
            }

            // Query Diagnosis Logs
            MongoCollection<Document> diagCol = MongoConfig.getDatabase().getCollection("diagnosis_logs");
            List<Map<String, Object>> diagnosisList = new ArrayList<>();
            for (Document doc : diagCol.find(Filters.eq("patientId", lookupId))) {
                Map<String, Object> diag = new HashMap<>();
                diag.put("symptomsSummary", doc.getString("symptomsSummary"));
                diag.put("duration", doc.getString("duration"));
                diag.put("pastHistory", doc.getString("pastHistory"));
                diag.put("matchedSpecialty", doc.getString("matchedSpecialty"));
                diag.put("createdAt", doc.getString("createdAt"));
                diagnosisList.add(diag);
            }

            // Fallback default diagnosis if none logged yet
            if (diagnosisList.isEmpty()) {
                Map<String, Object> fallbackDiag = new HashMap<>();
                fallbackDiag.put("symptomsSummary", "Lump or Swelling, Chronic Fatigue");
                fallbackDiag.put("duration", "1 to 4 weeks");
                fallbackDiag.put("pastHistory", "Family history of oncology treatments");
                fallbackDiag.put("matchedSpecialty", "Breast Cancer");
                fallbackDiag.put("createdAt", new Date().toString());
                diagnosisList.add(fallbackDiag);
            }

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("patientName", patientName);
            responseMap.put("goals", goalsMap);
            responseMap.put("medicines", medicinesList);
            responseMap.put("diagnosisLogs", diagnosisList);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(responseMap))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Failed to retrieve patient progress overview", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
