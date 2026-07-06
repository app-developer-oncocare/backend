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
public class DiagnosisLog {
    private String id;
    private String patientId;
    private String symptomsSummary;
    private String duration;
    private String pastHistory;
    private String matchedSpecialty;
    private String createdAt;
}
