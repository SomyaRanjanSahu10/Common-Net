package com.mailflow.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.List;
import java.util.Map;

/** Migrated from the two inline `app.get(...)` handlers in server.js
 *  (calendar holidays + health check). Data is kept identical. */
@RestController
public class CalendarController {

    @Value("${spring.mail.username:}")
    private String smtpUser;

    public record CalendarEvent(String date, String name, String type) {}

    @GetMapping("/api/calendar/holidays/{year}")
    public Map<String, Object> holidays(@PathVariable(required = false) String year) {
        int y;
        try {
            y = Integer.parseInt(year);
        } catch (Exception e) {
            y = Year.now().getValue();
        }
        List<CalendarEvent> events = List.of(
                new CalendarEvent(y + "-01-01", "New Year's Day", "holiday"),
                new CalendarEvent(y + "-01-26", "Republic Day (India)", "holiday"),
                new CalendarEvent(y + "-02-14", "Valentine's Day", "event"),
                new CalendarEvent(y + "-03-08", "International Women's Day", "event"),
                new CalendarEvent(y + "-03-21", "Holi", "holiday"),
                new CalendarEvent(y + "-04-14", "Dr. Ambedkar Jayanti", "holiday"),
                new CalendarEvent(y + "-05-01", "International Workers' Day", "holiday"),
                new CalendarEvent(y + "-06-21", "International Yoga Day", "event"),
                new CalendarEvent(y + "-08-15", "Independence Day (India)", "holiday"),
                new CalendarEvent(y + "-09-05", "Teachers' Day (India)", "event"),
                new CalendarEvent(y + "-10-02", "Gandhi Jayanti", "holiday"),
                new CalendarEvent(y + "-10-24", "Dussehra", "holiday"),
                new CalendarEvent(y + "-11-01", "Diwali", "holiday"),
                new CalendarEvent(y + "-11-14", "Children's Day (India)", "event"),
                new CalendarEvent(y + "-12-25", "Christmas Day", "holiday"),
                new CalendarEvent(y + "-12-31", "New Year's Eve", "event")
        );
        return Map.of("events", events);
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "Common Net API Running \u2705", "smtp", smtpUser != null && !smtpUser.isBlank());
    }
}
