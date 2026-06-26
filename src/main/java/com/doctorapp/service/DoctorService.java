package com.doctorapp.service;

import com.doctorapp.config.MongoConfig;
import com.doctorapp.model.User;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DoctorService {
    private static final Logger LOGGER = Logger.getLogger(DoctorService.class.getName());
    private static final String USERS_COLLECTION = "users";

    private MongoCollection<Document> getUsersCollection() {
        MongoDatabase db = MongoConfig.getDatabase();
        return db.getCollection(USERS_COLLECTION);
    }

    /**
     * Retrieves a list of all doctors from the database.
     */
    public List<User> getAllDoctors() {
        MongoCollection<Document> collection = getUsersCollection();
        List<User> doctors = new ArrayList<>();

        for (Document doc : collection.find(Filters.eq("role", "DOCTOR"))) {
            User user = AuthService.mapDocToUser(doc);
            if (user != null) {
                doctors.add(user);
            }
        }
        return doctors;
    }

    /**
     * Retrieves a single doctor by their MongoDB ID.
     */
    public User getDoctorById(String doctorId) {
        if (!ObjectId.isValid(doctorId)) {
            throw new IllegalArgumentException("Invalid doctor ID format.");
        }
        MongoCollection<Document> collection = getUsersCollection();
        Document doc = collection.find(
            Filters.and(
                Filters.eq("_id", new ObjectId(doctorId)),
                Filters.eq("role", "DOCTOR")
            )
        ).first();

        if (doc == null) {
            throw new IllegalArgumentException("Doctor not found with ID: " + doctorId);
        }
        return AuthService.mapDocToUser(doc);
    }

    /**
     * Updates the weekly schedule for a doctor.
     * The schedule map is structured as: { "monday": ["09:00-12:00", "14:00-17:00"], ... }
     */
    public User updateSchedule(String doctorId, Map<String, List<String>> schedule) {
        if (!ObjectId.isValid(doctorId)) {
            throw new IllegalArgumentException("Invalid doctor ID format.");
        }
        MongoCollection<Document> collection = getUsersCollection();

        // Verify doctor exists
        Document existing = collection.find(
            Filters.and(
                Filters.eq("_id", new ObjectId(doctorId)),
                Filters.eq("role", "DOCTOR")
            )
        ).first();

        if (existing == null) {
            throw new IllegalArgumentException("Doctor not found with ID: " + doctorId);
        }

        // Build the schedule sub-document
        Document scheduleDoc = new Document();
        for (Map.Entry<String, List<String>> entry : schedule.entrySet()) {
            scheduleDoc.append(entry.getKey().toLowerCase(), entry.getValue());
        }

        // Update profile.schedule in MongoDB
        collection.updateOne(
            Filters.eq("_id", new ObjectId(doctorId)),
            Updates.set("profile.schedule", scheduleDoc)
        );

        // Fetch and return the updated document
        Document updated = collection.find(Filters.eq("_id", new ObjectId(doctorId))).first();
        return AuthService.mapDocToUser(updated);
    }

    /**
     * Updates the full profile of a doctor including name and nested profile attributes.
     */
    public User updateProfile(String doctorId, User updatedUser) {
        if (!ObjectId.isValid(doctorId)) {
            throw new IllegalArgumentException("Invalid doctor ID format.");
        }
        MongoCollection<Document> collection = getUsersCollection();

        // Verify doctor exists
        Document existing = collection.find(
            Filters.and(
                Filters.eq("_id", new ObjectId(doctorId)),
                Filters.eq("role", "DOCTOR")
            )
        ).first();

        if (existing == null) {
            throw new IllegalArgumentException("Doctor not found with ID: " + doctorId);
        }

        // Build updates
        List<org.bson.conversions.Bson> updates = new ArrayList<>();
        if (updatedUser.getName() != null) {
            updates.add(Updates.set("name", updatedUser.getName()));
        }
        
        if (updatedUser.getProfile() != null) {
            User.Profile profile = updatedUser.getProfile();
            if (profile.getSpecialization() != null) {
                updates.add(Updates.set("profile.specialization", profile.getSpecialization()));
            }
            if (profile.getEducation() != null) {
                updates.add(Updates.set("profile.education", profile.getEducation()));
            }
            if (profile.getCity() != null) {
                updates.add(Updates.set("profile.city", profile.getCity()));
            }
            if (profile.getHospital() != null) {
                updates.add(Updates.set("profile.hospital", profile.getHospital()));
            }
            if (profile.getDescription() != null) {
                updates.add(Updates.set("profile.description", profile.getDescription()));
            }
            if (profile.getProfilePicture() != null) {
                updates.add(Updates.set("profile.profilePicture", profile.getProfilePicture()));
            }
            if (profile.getPictures() != null) {
                List<Document> pictureDocs = new ArrayList<>();
                for (User.MediaItem item : profile.getPictures()) {
                    if (item != null) {
                        pictureDocs.add(new Document("url", item.getUrl()).append("caption", item.getCaption()));
                    }
                }
                updates.add(Updates.set("profile.pictures", pictureDocs));
            }
            if (profile.getVoice() != null) {
                updates.add(Updates.set("profile.voice", new Document("url", profile.getVoice().getUrl())
                        .append("caption", profile.getVoice().getCaption())));
            }
            if (profile.getVideo() != null) {
                updates.add(Updates.set("profile.video", new Document("url", profile.getVideo().getUrl())
                        .append("caption", profile.getVideo().getCaption())));
            }
            if (profile.getLicenses() != null) {
                List<Document> licenseDocs = new ArrayList<>();
                for (User.MediaItem item : profile.getLicenses()) {
                    if (item != null) {
                        licenseDocs.add(new Document("url", item.getUrl()).append("caption", item.getCaption()));
                    }
                }
                updates.add(Updates.set("profile.licenses", licenseDocs));
            }
            if (profile.getSchedule() != null) {
                Document scheduleDoc = new Document();
                for (Map.Entry<String, List<String>> entry : profile.getSchedule().entrySet()) {
                    scheduleDoc.append(entry.getKey().toLowerCase(), entry.getValue());
                }
                updates.add(Updates.set("profile.schedule", scheduleDoc));
            }
        }

        if (!updates.isEmpty()) {
            collection.updateOne(
                Filters.eq("_id", new ObjectId(doctorId)),
                Updates.combine(updates)
            );
        }

        // Fetch and return the updated document
        Document updated = collection.find(Filters.eq("_id", new ObjectId(doctorId))).first();
        return AuthService.mapDocToUser(updated);
    }
}
