package com.mailflow.controller;

import com.mailflow.exception.ApiException;
import com.mailflow.model.Meeting;
import com.mailflow.model.User;
import com.mailflow.repository.MeetingRepository;
import com.mailflow.repository.UserRepository;
import com.mailflow.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    public MeetingController(MeetingRepository meetingRepository, UserRepository userRepository) {
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
    }

    public record ParticipantInput(String id) {}

    public record CreateMeetingRequest(
            String title,
            String startTime,
            Integer durationMinutes,
            String description,
            List<ParticipantInput> participants) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal UserPrincipal me,
                                       @RequestBody CreateMeetingRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()
                || request.startTime() == null || request.startTime().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Meeting title and start time are required");
        }
        final Instant start;
        try { start = Instant.parse(request.startTime()); }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "startTime must be a valid ISO instant"); }
        if (!start.isAfter(Instant.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Meeting time must be in the future");

        int duration = request.durationMinutes() == null ? 30 : request.durationMinutes();
        if (duration < 1 || duration > 1440)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duration must be between 1 and 1440 minutes");

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (request.participants() != null) {
            for (ParticipantInput p : request.participants()) {
                if (p != null && p.id() != null && !p.id().isBlank()) ids.add(p.id());
            }
        }
        ids.remove(me.getId());

        List<Meeting.Participant> participants = new ArrayList<>();
        for (String id : ids) {
            User u = userRepository.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Selected user was not found"));
            participants.add(Meeting.Participant.builder().user(u.getId()).name(u.getName()).email(u.getEmail()).build());
        }

        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        Meeting meeting = Meeting.builder()
                .organizer(me.getId())
                .title(request.title().trim())
                .startTime(start)
                .durationMinutes(duration)
                .description(request.description() == null ? "" : request.description())
                .meetingLink("https://meet.mailflow.app/" + code)
                .participants(participants)
                .build();
        meeting = meetingRepository.save(meeting);
        return Map.of("message", "Meeting scheduled successfully", "meeting", meeting);
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal UserPrincipal me) {
        Map<String, Meeting> unique = new LinkedHashMap<>();
        meetingRepository.findByOrganizerOrderByStartTimeAsc(me.getId()).forEach(m -> unique.put(m.getId(), m));
        meetingRepository.findByParticipantsUserOrderByStartTimeAsc(me.getId()).forEach(m -> unique.putIfAbsent(m.getId(), m));
        List<Meeting> meetings = unique.values().stream()
                .sorted(Comparator.comparing(Meeting::getStartTime))
                .collect(Collectors.toList());
        return Map.of("meetings", meetings);
    }
}
