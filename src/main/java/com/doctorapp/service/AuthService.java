package com.doctorapp.service;

import com.doctorapp.config.MongoConfig;
import com.doctorapp.model.User;
import com.doctorapp.util.BCryptUtil;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private static final String USERS_COLLECTION = "users";

    private MongoCollection<Document> getUsersCollection() {
        MongoDatabase db = MongoConfig.getDatabase();
        return db.getCollection(USERS_COLLECTION);
    }

    // Register a new user with mobile number
    public User register(User user) throws Exception {
        MongoCollection<Document> collection = getUsersCollection();

        // 1. Check if user already exists
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            Document existing = collection.find(Filters.eq("email", user.getEmail().trim().toLowerCase())).first();
            if (existing != null) {
                throw new IllegalArgumentException("User with this email already exists.");
            }
        } else if (user.getMobileNo() != null && !user.getMobileNo().trim().isEmpty()) {
            String countryCode = user.getCountryCode() != null ? user.getCountryCode().trim() : "";
            Document existing = collection.find(Filters.and(
                    Filters.eq("countryCode", countryCode),
                    Filters.eq("mobileNo", user.getMobileNo().trim()))).first();
            if (existing != null) {
                throw new IllegalArgumentException("User with this mobile number and country code already exists.");
            }
        } else {
            throw new IllegalArgumentException("Either email or mobile number is required for registration.");
        }

        user.setCreatedAt(new Date());

        // 2. Document structure mapping
        Document userDoc = new Document()
                .append("name", user.getName())
                .append("role", user.getRole().toString())
                .append("createdAt", user.getCreatedAt());

        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            userDoc.append("email", user.getEmail().trim().toLowerCase());
        }
        if (user.getMobileNo() != null && !user.getMobileNo().trim().isEmpty()) {
            String countryCode = user.getCountryCode() != null ? user.getCountryCode().trim() : "";
            userDoc.append("mobileNo", user.getMobileNo().trim())
                    .append("countryCode", countryCode);
        }

        if (user.getProfile() != null) {
            Document profileDoc = new Document();
            if (user.getProfile().getSpecialization() != null) {
                profileDoc.append("specialization", user.getProfile().getSpecialization());
            }
            if (user.getProfile().getConsultationFee() != null) {
                profileDoc.append("consultationFee", user.getProfile().getConsultationFee());
            }
            profileDoc.append("remainingCalls",
                    user.getProfile().getRemainingCalls() != null ? user.getProfile().getRemainingCalls() : 0);
            profileDoc.append("totalCalls",
                    user.getProfile().getTotalCalls() != null ? user.getProfile().getTotalCalls() : 0);
            profileDoc.append("remainingMeetings",
                    user.getProfile().getRemainingMeetings() != null ? user.getProfile().getRemainingMeetings() : 0);
            profileDoc.append("totalMeetings",
                    user.getProfile().getTotalMeetings() != null ? user.getProfile().getTotalMeetings() : 0);
            profileDoc.append("paymentsReceived",
                    user.getProfile().getPaymentsReceived() != null ? user.getProfile().getPaymentsReceived() : 0.0);

            // Map new profile builder fields
            if (user.getProfile().getEducation() != null) {
                profileDoc.append("education", user.getProfile().getEducation());
            }
            if (user.getProfile().getCity() != null) {
                profileDoc.append("city", user.getProfile().getCity());
            }
            if (user.getProfile().getHospital() != null) {
                profileDoc.append("hospital", user.getProfile().getHospital());
            }
            if (user.getProfile().getDescription() != null) {
                profileDoc.append("description", user.getProfile().getDescription());
            }
            if (user.getProfile().getProfilePicture() != null) {
                profileDoc.append("profilePicture", user.getProfile().getProfilePicture());
            }
            if (user.getProfile().getPictures() != null) {
                java.util.List<Document> pictureDocs = new java.util.ArrayList<>();
                for (User.MediaItem item : user.getProfile().getPictures()) {
                    if (item != null) {
                        pictureDocs.add(new Document("url", item.getUrl()).append("caption", item.getCaption()));
                    }
                }
                profileDoc.append("pictures", pictureDocs);
            }
            if (user.getProfile().getVoice() != null) {
                profileDoc.append("voice", new Document("url", user.getProfile().getVoice().getUrl())
                        .append("caption", user.getProfile().getVoice().getCaption()));
            }
            if (user.getProfile().getVideo() != null) {
                profileDoc.append("video", new Document("url", user.getProfile().getVideo().getUrl())
                        .append("caption", user.getProfile().getVideo().getCaption()));
            }
            if (user.getProfile().getLicenses() != null) {
                java.util.List<Document> licenseDocs = new java.util.ArrayList<>();
                for (User.MediaItem item : user.getProfile().getLicenses()) {
                    if (item != null) {
                        licenseDocs.add(new Document("url", item.getUrl()).append("caption", item.getCaption()));
                    }
                }
                profileDoc.append("licenses", licenseDocs);
            }
            if (user.getProfile().getSchedule() != null) {
                profileDoc.append("schedule", new Document(user.getProfile().getSchedule()));
            }

            userDoc.append("profile", profileDoc);
        }

        // 3. Insert into database
        collection.insertOne(userDoc);

        // 4. Populate ID
        ObjectId generatedId = userDoc.getObjectId("_id");
        user.setId(generatedId.toHexString());

        return user;
    }

    // Login a user by country code/mobile number OR email
    public User login(String countryCode, String mobileNo, String email) throws Exception {
        MongoCollection<Document> collection = getUsersCollection();
        Document doc = null;

        // 1. Find user by email or by country code and mobile number
        if (email != null && !email.trim().isEmpty()) {
            doc = collection.find(Filters.eq("email", email.trim().toLowerCase())).first();
            if (doc == null) {
                throw new IllegalArgumentException("User with this email not found.");
            }
        } else if (mobileNo != null && !mobileNo.trim().isEmpty()) {
            String cc = countryCode != null ? countryCode.trim() : "";
            doc = collection.find(Filters.and(
                    Filters.eq("countryCode", cc),
                    Filters.eq("mobileNo", mobileNo.trim()))).first();
            if (doc == null) {
                throw new IllegalArgumentException("User with this mobile number and country code not found.");
            }
        } else {
            throw new IllegalArgumentException("Either email or mobile number is required for login.");
        }

        // 2. Map document to User model
        return mapDocToUser(doc);
    }

    // Helper: Map MongoDB document to User object
    @SuppressWarnings("unchecked")
    public static User mapDocToUser(Document doc) {
        if (doc == null)
            return null;

        User user = new User();
        user.setId(doc.getObjectId("_id").toHexString());
        user.setName(doc.getString("name"));
        user.setMobileNo(doc.getString("mobileNo"));
        user.setCountryCode(doc.getString("countryCode"));
        user.setEmail(doc.getString("email"));

        String roleStr = doc.getString("role");
        if (roleStr != null) {
            user.setRole(User.Role.valueOf(roleStr));
        }

        user.setCreatedAt(doc.getDate("createdAt"));

        Document profileDoc = (Document) doc.get("profile");
        if (profileDoc != null) {
            User.Profile profile = new User.Profile();
            profile.setSpecialization(profileDoc.getString("specialization"));

            Object fee = profileDoc.get("consultationFee");
            if (fee instanceof Number) {
                profile.setConsultationFee(((Number) fee).doubleValue());
            }

            profile.setRemainingCalls(profileDoc.getInteger("remainingCalls"));
            profile.setTotalCalls(profileDoc.getInteger("totalCalls"));
            profile.setRemainingMeetings(profileDoc.getInteger("remainingMeetings"));
            profile.setTotalMeetings(profileDoc.getInteger("totalMeetings"));

            Object payments = profileDoc.get("paymentsReceived");
            if (payments instanceof Number) {
                profile.setPaymentsReceived(((Number) payments).doubleValue());
            }

            // Map weekly schedule: { "monday": ["09:00-12:00", ...], ... }
            Document scheduleDoc = (Document) profileDoc.get("schedule");
            if (scheduleDoc != null) {
                java.util.Map<String, java.util.List<String>> scheduleMap = new java.util.LinkedHashMap<>();
                for (String day : scheduleDoc.keySet()) {
                    Object slotsObj = scheduleDoc.get(day);
                    if (slotsObj instanceof java.util.List) {
                        java.util.List<String> slots = new java.util.ArrayList<>();
                        for (Object slot : (java.util.List<?>) slotsObj) {
                            slots.add(slot.toString());
                        }
                        scheduleMap.put(day, slots);
                    }
                }
                profile.setSchedule(scheduleMap);
            }

            // Map new profile builder fields back
            if (profileDoc.get("education") instanceof java.util.List) {
                profile.setEducation((java.util.List<String>) profileDoc.get("education"));
            }
            profile.setCity(profileDoc.getString("city"));
            if (profileDoc.get("hospital") instanceof java.util.List) {
                profile.setHospital((java.util.List<String>) profileDoc.get("hospital"));
            }
            profile.setDescription(profileDoc.getString("description"));
            profile.setProfilePicture(profileDoc.getString("profilePicture"));
            if (profileDoc.get("pictures") instanceof java.util.List) {
                java.util.List<?> picturesList = (java.util.List<?>) profileDoc.get("pictures");
                java.util.List<User.MediaItem> items = new java.util.ArrayList<>();
                for (Object obj : picturesList) {
                    if (obj instanceof Document) {
                        Document d = (Document) obj;
                        items.add(new User.MediaItem(d.getString("url"), d.getString("caption")));
                    } else if (obj instanceof String) {
                        items.add(new User.MediaItem((String) obj, ""));
                    }
                }
                profile.setPictures(items);
            }
            Object voiceObj = profileDoc.get("voice");
            if (voiceObj instanceof Document) {
                Document d = (Document) voiceObj;
                profile.setVoice(new User.MediaItem(d.getString("url"), d.getString("caption")));
            } else if (voiceObj instanceof String) {
                profile.setVoice(new User.MediaItem((String) voiceObj, ""));
            }
            Object videoObj = profileDoc.get("video");
            if (videoObj instanceof Document) {
                Document d = (Document) videoObj;
                profile.setVideo(new User.MediaItem(d.getString("url"), d.getString("caption")));
            } else if (videoObj instanceof String) {
                profile.setVideo(new User.MediaItem((String) videoObj, ""));
            }
            Object licensesObj = profileDoc.get("licenses");
            java.util.List<User.MediaItem> licenses = new java.util.ArrayList<>();
            if (licensesObj instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) licensesObj;
                for (Object item : list) {
                    if (item instanceof Document) {
                        Document d = (Document) item;
                        licenses.add(new User.MediaItem(d.getString("url"), d.getString("caption")));
                    }
                }
            } else {
                Object licenseObj = profileDoc.get("license");
                if (licenseObj instanceof Document) {
                    Document d = (Document) licenseObj;
                    licenses.add(new User.MediaItem(d.getString("url"), d.getString("caption")));
                } else if (licenseObj instanceof String) {
                    licenses.add(new User.MediaItem((String) licenseObj, ""));
                } else {
                    String legacyLicense = profileDoc.getString("licenseUrl");
                    if (legacyLicense != null) {
                        licenses.add(new User.MediaItem(legacyLicense, ""));
                    }
                }
            }
            if (!licenses.isEmpty()) {
                profile.setLicenses(licenses);
            }

            user.setProfile(profile);
        }

        return user;
    }
}
