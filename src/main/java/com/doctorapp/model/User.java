package com.doctorapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    private String id; // Represents the MongoDB hex string _id
    private String name;
    private String mobileNo;
    private String countryCode;
    private String email;
    private Role role;
    private Date createdAt;
    private Profile profile;

    public enum Role {
        PATIENT,
        DOCTOR,
        ADMIN,
        MANAGER
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        private String specialization;
        private Double consultationFee;
        private Integer remainingCalls;
        private Integer totalCalls;
        private Integer remainingMeetings;
        private Integer totalMeetings;
        private Double paymentsReceived;
        // Weekly schedule: key = day (e.g. "monday"), value = list of time slots (e.g. ["09:00-12:00"])
        private Map<String, List<String>> schedule;
        
        // New Profile Builder fields
        private List<String> education;
        private String city;
        private List<String> hospital;
        private String description;
        private String profilePicture;
        private List<MediaItem> pictures;
        private MediaItem voice;
        private MediaItem video;
        private List<MediaItem> licenses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaItem {
        private String url;
        private String caption;
    }
}
