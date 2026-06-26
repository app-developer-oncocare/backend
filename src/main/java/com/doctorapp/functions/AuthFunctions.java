package com.doctorapp.functions;

import com.doctorapp.model.User;
import com.doctorapp.service.AuthService;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class AuthFunctions {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AuthService authService = new AuthService();

    @FunctionName("register")
    public HttpResponseMessage register(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "auth/register")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing registration request.");
        
        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            // Parse request body
            Map<String, Object> bodyMap = MAPPER.readValue(body, Map.class);
            String name = (String) bodyMap.get("name");
            String mobileNo = (String) bodyMap.get("mobileNo");
            String countryCode = (String) bodyMap.get("countryCode");
            String email = (String) bodyMap.get("email");
            String specialization = (String) bodyMap.get("specialization");

            boolean hasPhone = mobileNo != null && !mobileNo.isEmpty() && countryCode != null && !countryCode.isEmpty();
            boolean hasEmail = email != null && !email.isEmpty();

            if (name == null || name.isEmpty() ||
                specialization == null || specialization.isEmpty() ||
                (!hasPhone && !hasEmail)) {
                
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"name, specialization, and either email or (mobileNo + countryCode) are required fields.\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }

            // Extract optional profile builder fields
            java.util.List<String> education = (java.util.List<String>) bodyMap.get("education");
            String city = (String) bodyMap.get("city");
            java.util.List<String> hospital = (java.util.List<String>) bodyMap.get("hospital");
            String description = (String) bodyMap.get("description");
            
            String profilePicture = (String) bodyMap.get("profilePicture");
            
            java.util.List<Map<String, String>> picturesList = (java.util.List<Map<String, String>>) bodyMap.get("pictures");
            java.util.List<User.MediaItem> pictures = null;
            if (picturesList != null) {
                pictures = new java.util.ArrayList<>();
                for (Map<String, String> m : picturesList) {
                    if (m != null) {
                        pictures.add(new User.MediaItem(m.get("url"), m.get("caption")));
                    }
                }
            }

            Map<String, String> voiceMap = (Map<String, String>) bodyMap.get("voice");
            User.MediaItem voice = voiceMap != null ? new User.MediaItem(voiceMap.get("url"), voiceMap.get("caption")) : null;

            Map<String, String> videoMap = (Map<String, String>) bodyMap.get("video");
            User.MediaItem video = videoMap != null ? new User.MediaItem(videoMap.get("url"), videoMap.get("caption")) : null;

            java.util.List<Map<String, String>> licensesList = (java.util.List<Map<String, String>>) bodyMap.get("licenses");
            java.util.List<User.MediaItem> licenses = null;
            if (licensesList != null) {
                licenses = new java.util.ArrayList<>();
                for (Map<String, String> m : licensesList) {
                    if (m != null) {
                        licenses.add(new User.MediaItem(m.get("url"), m.get("caption")));
                    }
                }
            } else {
                String licenseUrl = (String) bodyMap.get("licenseUrl");
                if (licenseUrl == null || licenseUrl.isEmpty()) {
                    Object licenseObj = bodyMap.get("license");
                    if (licenseObj instanceof Map) {
                        Map<String, String> lMap = (Map<String, String>) licenseObj;
                        licenseUrl = lMap.get("url");
                    } else if (licenseObj instanceof String) {
                        licenseUrl = (String) licenseObj;
                    }
                }
                if (licenseUrl != null && !licenseUrl.isEmpty()) {
                    licenses = new java.util.ArrayList<>();
                    licenses.add(new User.MediaItem(licenseUrl, ""));
                }
            }

            java.util.Map<String, java.util.List<String>> schedule = (java.util.Map<String, java.util.List<String>>) bodyMap.get("schedule");

            // Create user object with role DOCTOR
            User.Profile profile = User.Profile.builder()
                    .specialization(specialization)
                    .consultationFee(250.00)
                    .remainingCalls(2)
                    .totalCalls(5)
                    .remainingMeetings(4)
                    .totalMeetings(5)
                    .paymentsReceived(9500.00)
                    .education(education)
                    .city(city)
                    .hospital(hospital)
                    .description(description)
                    .profilePicture(profilePicture)
                    .pictures(pictures)
                    .voice(voice)
                    .video(video)
                    .licenses(licenses)
                    .schedule(schedule)
                    .build();

            User user = User.builder()
                    .name(name)
                    .mobileNo(mobileNo)
                    .countryCode(countryCode)
                    .email(email)
                    .role(User.Role.DOCTOR)
                    .profile(profile)
                    .build();

            // Call Auth Service
            User registeredUser = authService.register(user);
            
            // Generate JWT Token
            String subject = registeredUser.getEmail() != null && !registeredUser.getEmail().isEmpty() 
                    ? registeredUser.getEmail() 
                    : registeredUser.getMobileNo();
            String token = JwtUtil.generateToken(registeredUser.getId(), subject, registeredUser.getRole().toString());

            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", registeredUser);
            responseData.put("token", token);

            return request.createResponseBuilder(HttpStatus.CREATED)
                    .body(MAPPER.writeValueAsString(responseData))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (IllegalArgumentException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"" + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Registration failed", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"An unexpected error occurred during registration.\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }

    @FunctionName("login")
    public HttpResponseMessage login(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "auth/login")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Processing login request.");
        
        String body = request.getBody().orElse("");
        if (body.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Request body is empty.")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            Map<String, String> credentials = MAPPER.readValue(body, Map.class);
            String mobileNo = credentials.get("mobileNo");
            String countryCode = credentials.get("countryCode");
            String email = credentials.get("email");

            boolean hasPhone = mobileNo != null && !mobileNo.isEmpty() && countryCode != null && !countryCode.isEmpty();
            boolean hasEmail = email != null && !email.isEmpty();

            if (!hasPhone && !hasEmail) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Either email or (mobileNo and countryCode) are required fields.\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }

            // Authenticate user
            User user = authService.login(countryCode, mobileNo, email);
            
            // Generate JWT Token
            String subject = user.getEmail() != null && !user.getEmail().isEmpty() ? user.getEmail() : user.getMobileNo();
            String token = JwtUtil.generateToken(user.getId(), subject, user.getRole().toString());

            // Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("user", user);
            responseData.put("token", token);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(MAPPER.writeValueAsString(responseData))
                    .header("Content-Type", "application/json")
                    .build();

        } catch (IllegalArgumentException e) {
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"" + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Login failed", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"An unexpected error occurred during login.\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}
