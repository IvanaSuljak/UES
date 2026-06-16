package com.example.newnow.controller;

import com.example.newnow.model.Event;
import com.example.newnow.model.Location;
import com.example.newnow.repository.EventRepository;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.service.LocationReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/home")
@CrossOrigin(origins = "*")
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationReviewService reviewService;

    @GetMapping
    public ResponseEntity<?> getHomepageData() {
        try {
            Map<String, Object> response = new HashMap<>();

            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);

            List<Event> todayEvents = eventRepository.findAll().stream()
                    .filter(e -> e.getDateTime() != null &&
                            e.getDateTime().isAfter(startOfDay) &&
                            e.getDateTime().isBefore(endOfDay))
                    .sorted(Comparator.comparing(Event::getDateTime))
                    .limit(6) // Maksimalno 6 događaja
                    .collect(Collectors.toList());

            List<Location> allLocations = locationRepository.findAllValidLocations();

            List<Map<String, Object>> topLocations = allLocations.stream()
                    .map(location -> {
                        Map<String, Object> locationData = new HashMap<>();
                        locationData.put("id", location.getId());
                        locationData.put("name", location.getName());
                        locationData.put("address", location.getAddress());
                        locationData.put("type", location.getType());
                        locationData.put("imageUrl", location.getImageUrl());

                        Double avgRating = reviewService.getAverageRatingForLocation(location.getId());
                        Long totalReviews = reviewService.getTotalReviewsForLocation(location.getId());

                        locationData.put("averageRating", avgRating != null ? avgRating : 0.0);
                        locationData.put("totalReviews", totalReviews != null ? totalReviews : 0L);

                        return locationData;
                    })
                    .sorted((a, b) -> {
                        Double ratingA = (Double) a.get("averageRating");
                        Double ratingB = (Double) b.get("averageRating");
                        // Ako imaju istu ocenu, sortiraj po broju utisaka
                        if (ratingB.equals(ratingA)) {
                            Long reviewsA = (Long) a.get("totalReviews");
                            Long reviewsB = (Long) b.get("totalReviews");
                            return reviewsB.compareTo(reviewsA);
                        }
                        return ratingB.compareTo(ratingA); // Sortiranje opadajuće
                    })
                    .limit(4) // Top 4 lokacije
                    .collect(Collectors.toList());

            // K8: najskorija 3 utiska sa najpopularnijeg mesta
            List<Map<String, Object>> recentReviews = new java.util.ArrayList<>();
            if (!topLocations.isEmpty()) {
                Long topLocationId = (Long) topLocations.get(0).get("id");
                List<com.example.newnow.model.LocationReview> recent =
                        reviewService.findByLocationIdSortedByDateDesc(topLocationId)
                                .stream()
                                .filter(r -> !r.getIsDeleted() && !r.getIsHidden())
                                .limit(3)
                                .collect(Collectors.toList());

                for (com.example.newnow.model.LocationReview r : recent) {
                    Map<String, Object> rv = new HashMap<>();
                    rv.put("id", r.getId());
                    rv.put("comment", r.getComment());
                    rv.put("averageRating", r.getAverageRating());
                    rv.put("createdAt", r.getCreatedAt());
                    rv.put("locationName", topLocations.get(0).get("name"));
                    rv.put("locationId", topLocationId);
                    if (r.getUser() != null) {
                        rv.put("userName", r.getUser().getFullName());
                    }
                    recentReviews.add(rv);
                }
            }

            response.put("todayEvents", todayEvents);
            response.put("topLocations", topLocations);
            response.put("recentReviews", recentReviews);

            logger.info("Homepage data - Dogadjaji: {}, Top lokacije: {}, Skoriji utisci: {}",
                    todayEvents.size(), topLocations.size(), recentReviews.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("❌ Greška u HomeController: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Greška: " + e.getMessage());
        }
    }
}