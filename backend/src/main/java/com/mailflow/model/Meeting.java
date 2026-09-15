package com.mailflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "meetings")
public class Meeting {
    @Id
    private String id;
    private String organizer;
    private String title;
    private Instant startTime;
    private Integer durationMinutes;
    @Builder.Default private String description = "";
    @Builder.Default private String meetingLink = "";
    @Builder.Default private List<Participant> participants = new ArrayList<>();

    @CreatedDate private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        private String user;
        private String name;
        private String email;
    }
}
