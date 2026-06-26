package com.doctorapp.service;

import com.doctorapp.config.MongoConfig;
import com.doctorapp.model.Appointment;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class AppointmentService {
    private static final Logger LOGGER = Logger.getLogger(AppointmentService.class.getName());
    private static final String APPOINTMENTS_COLLECTION = "appointments";

    private MongoCollection<Document> getAppointmentsCollection() {
        MongoDatabase db = MongoConfig.getDatabase();
        return db.getCollection(APPOINTMENTS_COLLECTION);
    }

    /**
     * Books a new appointment for a patient with a doctor.
     */
    public Appointment bookAppointment(Appointment appointment) {
        MongoCollection<Document> collection = getAppointmentsCollection();

        if (appointment.getDoctorId() == null || appointment.getDoctorId().isEmpty()) {
            throw new IllegalArgumentException("doctorId is required.");
        }
        if (appointment.getPatientName() == null || appointment.getPatientName().isEmpty()) {
            throw new IllegalArgumentException("patientName is required.");
        }
        if (appointment.getTimeSlot() == null || appointment.getTimeSlot().isEmpty()) {
            throw new IllegalArgumentException("timeSlot is required.");
        }

        Document doc = new Document()
                .append("doctorId", appointment.getDoctorId())
                .append("patientName", appointment.getPatientName())
                .append("patientInitials", appointment.getPatientInitials())
                .append("diagnosis", appointment.getDiagnosis())
                .append("stage", appointment.getStage() != null ? appointment.getStage() : "Follow-up")
                .append("timeSlot", appointment.getTimeSlot())
                .append("status", Appointment.Status.PENDING.toString())
                .append("createdAt", new Date());

        collection.insertOne(doc);

        ObjectId generatedId = doc.getObjectId("_id");
        appointment.setId(generatedId.toHexString());
        appointment.setStatus(Appointment.Status.PENDING);
        appointment.setCreatedAt(doc.getDate("createdAt"));

        return appointment;
    }

    /**
     * Returns all appointments for a specific doctor, sorted by newest first.
     */
    public List<Appointment> getAppointmentsForDoctor(String doctorId) {
        if (doctorId == null || doctorId.isEmpty()) {
            throw new IllegalArgumentException("doctorId is required.");
        }
        MongoCollection<Document> collection = getAppointmentsCollection();
        List<Appointment> result = new ArrayList<>();

        for (Document doc : collection.find(Filters.eq("doctorId", doctorId))
                .sort(Sorts.descending("createdAt"))) {
            result.add(mapDocToAppointment(doc));
        }
        return result;
    }

    /**
     * Updates the status of an appointment (CONFIRMED or CANCELLED).
     */
    public Appointment updateAppointmentStatus(String appointmentId, String status) {
        if (!ObjectId.isValid(appointmentId)) {
            throw new IllegalArgumentException("Invalid appointment ID format.");
        }

        Appointment.Status newStatus;
        try {
            newStatus = Appointment.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status. Must be PENDING, CONFIRMED, or CANCELLED.");
        }

        MongoCollection<Document> collection = getAppointmentsCollection();
        Document existing = collection.find(Filters.eq("_id", new ObjectId(appointmentId))).first();
        if (existing == null) {
            throw new IllegalArgumentException("Appointment not found with ID: " + appointmentId);
        }

        collection.updateOne(
            Filters.eq("_id", new ObjectId(appointmentId)),
            Updates.set("status", newStatus.toString())
        );

        Document updated = collection.find(Filters.eq("_id", new ObjectId(appointmentId))).first();
        return mapDocToAppointment(updated);
    }

    /**
     * Helper to map a MongoDB document to an Appointment model.
     */
    private static Appointment mapDocToAppointment(Document doc) {
        if (doc == null) return null;

        Appointment appointment = new Appointment();
        appointment.setId(doc.getObjectId("_id").toHexString());
        appointment.setDoctorId(doc.getString("doctorId"));
        appointment.setPatientName(doc.getString("patientName"));
        appointment.setPatientInitials(doc.getString("patientInitials"));
        appointment.setDiagnosis(doc.getString("diagnosis"));
        appointment.setStage(doc.getString("stage"));
        appointment.setTimeSlot(doc.getString("timeSlot"));
        appointment.setCreatedAt(doc.getDate("createdAt"));

        String statusStr = doc.getString("status");
        if (statusStr != null) {
            try {
                appointment.setStatus(Appointment.Status.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                appointment.setStatus(Appointment.Status.PENDING);
            }
        }
        return appointment;
    }
}
