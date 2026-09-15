package com.mailflow.repository;

import com.mailflow.model.Meeting;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MeetingRepository extends MongoRepository<Meeting, String> {
    List<Meeting> findByOrganizerOrderByStartTimeAsc(String organizer);
    List<Meeting> findByParticipantsUserOrderByStartTimeAsc(String user);
}
