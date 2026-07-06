package com.doctorapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeServiceRequest {
    private String id;
    private String patientId;
    private String serviceType;
    private String scheduledDate;
    private String address;
    private String specialNotes;
    private String status;
    private String createdAt;
}
