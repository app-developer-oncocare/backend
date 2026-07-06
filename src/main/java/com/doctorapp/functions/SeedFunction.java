package com.doctorapp.functions;

import com.doctorapp.config.MongoConfig;
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
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class SeedFunction {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("seed")
    public HttpResponseMessage seed(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "seed")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing database seeding request.");
        MongoDatabase db = MongoConfig.getDatabase();
        MongoCollection<Document> usersCollection = db.getCollection("users");
        MongoCollection<Document> appointmentsCollection = db.getCollection("appointments");

        try {
            // 1. Clear existing items to ensure fresh data
            usersCollection.deleteMany(new Document());
            appointmentsCollection.deleteMany(new Document());

            // 2. Create Dr. Madhur Singh User
            ObjectId doctorId = new ObjectId();
            Document doctorDoc = new Document()
                    .append("_id", doctorId)
                    .append("name", "Dr. Madhur Singh")
                    .append("mobileNo", "9999999999") // simple 10 digit number for dev testing
                    .append("countryCode", "+91")
                    .append("role", "DOCTOR")
                    .append("createdAt", new Date());

            Document profileDoc = new Document()
                    .append("specialization", "Oncologist")
                    .append("consultationFee", 250.00)
                    .append("remainingCalls", 2)
                    .append("totalCalls", 5)
                    .append("remainingMeetings", 4)
                    .append("totalMeetings", 5)
                    .append("paymentsReceived", 9500.00);
            
            doctorDoc.append("profile", profileDoc);
            usersCollection.insertOne(doctorDoc);

            // 2.5 Create System Admin User
            Document adminDoc = new Document()
                    .append("_id", new ObjectId())
                    .append("name", "System Admin")
                    .append("mobileNo", "8888888888")
                    .append("countryCode", "+91")
                    .append("role", "ADMIN")
                    .append("createdAt", new Date());
            usersCollection.insertOne(adminDoc);

            // 3. Create Sample Appointments linked to Dr. Layla
            Document app1 = new Document()
                    .append("patientName", "Mariam Matar")
                    .append("patientInitials", "MM")
                    .append("diagnosis", "Lung Cancer")
                    .append("stage", "Stage III")
                    .append("doctorId", doctorId.toHexString())
                    .append("timeSlot", "10 - 11 AM")
                    .append("status", "CONFIRMED")
                    .append("createdAt", new Date());

            Document app2 = new Document()
                    .append("patientName", "Fatima Hassan")
                    .append("patientInitials", "FH")
                    .append("diagnosis", "Breast Cancer")
                    .append("stage", "Stage II")
                    .append("doctorId", doctorId.toHexString())
                    .append("timeSlot", "11 - 12 PM")
                    .append("status", "CONFIRMED")
                    .append("createdAt", new Date());

            Document app3 = new Document()
                    .append("patientName", "Ahmed Ali")
                    .append("patientInitials", "AA")
                    .append("diagnosis", "Colon Cancer")
                    .append("stage", "Stage IV")
                    .append("doctorId", doctorId.toHexString())
                    .append("timeSlot", "02:00 PM - 03:00 PM")
                    .append("status", "CONFIRMED")
                    .append("createdAt", new Date());

            Document app4 = new Document()
                    .append("patientName", "Sarah Smith")
                    .append("patientInitials", "SS")
                    .append("diagnosis", "Brain Tumor")
                    .append("stage", "Stage I")
                    .append("doctorId", doctorId.toHexString())
                    .append("timeSlot", "03:00 PM - 04:00 PM")
                    .append("status", "CONFIRMED")
                    .append("createdAt", new Date());

            Document app5 = new Document()
                    .append("patientName", "Khalid Mansoor")
                    .append("patientInitials", "KM")
                    .append("diagnosis", "Prostate Cancer")
                    .append("stage", "Stage III")
                    .append("doctorId", doctorId.toHexString())
                    .append("timeSlot", "04:00 PM - 05:00 PM")
                    .append("status", "CONFIRMED")
                    .append("createdAt", new Date());

            appointmentsCollection.insertMany(Arrays.asList(app1, app2, app3, app4, app5));

            Map<String, String> responseMsg = new HashMap<>();
            responseMsg.put("message", "Database seeded successfully. Dr. Madhur Singh (+9999999999) and 5 appointments created.");

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(responseMsg))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Database seeding failed", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Database seeding failed: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
