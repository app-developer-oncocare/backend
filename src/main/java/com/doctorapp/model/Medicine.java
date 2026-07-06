package com.doctorapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Medicine {
    private String id;
    private String patientId;
    private String name;
    private String dosage;
    private String scheduleSlot; // "morning" | "afternoon" | "night"
    private String startDate;    // YYYY-MM-DD
    private String endDate;      // YYYY-MM-DD
    private Map<String, Boolean> compliance; // Key = YYYY-MM-DD, Value = checked status
}
