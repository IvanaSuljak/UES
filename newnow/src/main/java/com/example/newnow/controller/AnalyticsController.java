package com.example.newnow.controller;

import com.example.newnow.model.Event;
import com.example.newnow.model.Location;
import com.example.newnow.model.LocationReview;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.EventRepository;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.repository.LocationReviewRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @Autowired private EventRepository eventRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private LocationReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    /**
     * M4 — analitika za lokaciju u zadatom vremenskom opsegu.
     * Pristup: samo MANAGER date lokacije (ili ADMIN).
     * Parametri: startDate, endDate (ISO yyyy-MM-dd)
     */
    @GetMapping("/location/{locationId}")
    public ResponseEntity<?> getAnalytics(
            @PathVariable Long locationId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String email = jwtUtil.extractUsername(authHeader.substring(7));
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Lokacija nije pronađena"));

            if (user.getRole() == Role.MANAGER &&
                    (location.getManager() == null || !location.getManager().getId().equals(user.getId()))) {
                return ResponseEntity.status(403).body(Map.of("error", "Nemate pristup analitici ove lokacije."));
            }
            if (user.getRole() == Role.USER) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo menadžer može pristupiti analitici."));
            }

            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end   = LocalDate.parse(endDate).atTime(23, 59, 59);

            // ─── DOGAĐAJI ───
            List<Event> events = eventRepository.findByLocationIdAndPeriod(locationId, start, end);
            long totalEvents     = events.size();
            long regularEvents   = events.stream().filter(e -> Boolean.TRUE.equals(e.getIsRegular())).count();
            long irregularEvents = totalEvents - regularEvents;
            long freeEvents      = events.stream().filter(e -> e.getPrice() == null || e.getPrice() == 0).count();
            long paidEvents      = totalEvents - freeEvents;

            // Top 5 događaja po naslovu (po broju ponavljanja u periodu)
            Map<String, Long> eventCounts = events.stream()
                    .collect(Collectors.groupingBy(Event::getTitle, Collectors.counting()));
            List<Map<String, Object>> topEvents = eventCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("title", e.getKey());
                        m.put("occurrences", e.getValue());
                        return m;
                    })
                    .collect(Collectors.toList());

            // Događaji po mesecu (za bar chart)
            Map<String, Long> eventsPerMonth = new LinkedHashMap<>();
            events.stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getDateTime().getYear() + "-" + String.format("%02d", e.getDateTime().getMonthValue()),
                            Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> eventsPerMonth.put(e.getKey(), e.getValue()));

            // ─── UTISCI ───
            List<LocationReview> reviews = reviewRepository.findByLocationIdAndPeriod(locationId, start, end);
            long totalReviews = reviews.size();
            double avgRating = reviews.stream()
                    .mapToDouble(r -> r.getAverageRating() != null ? r.getAverageRating() : 0.0)
                    .average().orElse(0.0);

            // Prosečne ocene po kategorijama
            OptionalDouble avgPerformance = reviews.stream().filter(r -> r.getPerformanceRating() != null)
                    .mapToInt(LocationReview::getPerformanceRating).average();
            OptionalDouble avgSound = reviews.stream().filter(r -> r.getSoundLightRating() != null)
                    .mapToInt(LocationReview::getSoundLightRating).average();
            OptionalDouble avgSpace = reviews.stream().filter(r -> r.getSpaceRating() != null)
                    .mapToInt(LocationReview::getSpaceRating).average();
            OptionalDouble avgOverall = reviews.stream().filter(r -> r.getOverallRating() != null)
                    .mapToInt(LocationReview::getOverallRating).average();

            // Najskorija 3 utiska
            List<Map<String, Object>> recentReviews = reviews.stream()
                    .filter(r -> !r.getIsHidden())
                    .sorted(Comparator.comparing(LocationReview::getCreatedAt).reversed())
                    .limit(3)
                    .map(r -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("comment", r.getComment());
                        m.put("averageRating", r.getAverageRating());
                        m.put("createdAt", r.getCreatedAt());
                        if (r.getUser() != null) m.put("userName", r.getUser().getFullName());
                        return m;
                    })
                    .collect(Collectors.toList());

            // ─── TOP LOKACIJE (sve, po prosečnoj oceni) ───
            List<Map<String, Object>> topLocations = locationRepository.findAllValidLocations().stream()
                    .map(loc -> {
                        Double avg = reviewRepository.getAverageRatingForLocation(loc.getId());
                        Long cnt  = reviewRepository.getTotalReviewsForLocation(loc.getId());
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", loc.getId());
                        m.put("name", loc.getName());
                        m.put("averageRating", avg != null ? avg : 0.0);
                        m.put("totalReviews", cnt != null ? cnt : 0L);
                        return m;
                    })
                    .sorted(Comparator.comparingDouble(m -> -((Double) m.get("averageRating"))))
                    .limit(5)
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("locationName", location.getName());
            response.put("period", Map.of("start", startDate, "end", endDate));
            // Događaji
            response.put("totalEvents", totalEvents);
            response.put("regularEvents", regularEvents);
            response.put("irregularEvents", irregularEvents);
            response.put("freeEvents", freeEvents);
            response.put("paidEvents", paidEvents);
            response.put("topEvents", topEvents);
            response.put("eventsPerMonth", eventsPerMonth);
            // Utisci
            response.put("totalReviews", totalReviews);
            response.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
            response.put("avgPerformance", avgPerformance.isPresent() ? Math.round(avgPerformance.getAsDouble() * 10) / 10.0 : null);
            response.put("avgSound",       avgSound.isPresent()       ? Math.round(avgSound.getAsDouble() * 10) / 10.0 : null);
            response.put("avgSpace",       avgSpace.isPresent()       ? Math.round(avgSpace.getAsDouble() * 10) / 10.0 : null);
            response.put("avgOverall",     avgOverall.isPresent()     ? Math.round(avgOverall.getAsDouble() * 10) / 10.0 : null);
            response.put("recentReviews", recentReviews);
            // Top lokacije
            response.put("topLocations", topLocations);

            logger.info("M4 — Analitika za lokaciju {} ({}→{}): {} dogadjaja, {} utisaka",
                    location.getName(), startDate, endDate, totalEvents, totalReviews);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("M4 greška: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
