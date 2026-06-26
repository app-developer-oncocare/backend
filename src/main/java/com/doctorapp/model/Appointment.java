package com.doctorapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Appointment {
    private String id;
    private String patientName;
    private String patientInitials;
    private String diagnosis;
    private String stage;
    private String doctorId;
    private String timeSlot;
    private Status status;
    private Date createdAt;

    public enum Status {
        PENDING,
        CONFIRMED,
        CANCELLED
    }
}
