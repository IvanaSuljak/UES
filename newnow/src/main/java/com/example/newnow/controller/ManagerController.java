package com.example.newnow.controller;

import com.example.newnow.model.Location;
import com.example.newnow.model.LocationReview;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.service.LocationReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manager")
@CrossOrigin(origins = "*")
public class ManagerController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationReviewService reviewService;

    @Autowired
    private JwtUtil jwtUtil;

    //Lista svih mesta gde je manager
    @GetMapping("/dashboard")
    public ResponseEntity<?> getManagerDashboard(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User manager = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            if (manager.getRole() != Role.MANAGER) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo menadžeri mogu pristupiti ovoj ruti!"
                ));
            }

            List<Location> managedLocations = locationRepository.findAll().stream()
                    .filter(loc -> loc.getManager() != null &&
                            loc.getManager().getId().equals(manager.getId()))
                    .collect(Collectors.toList());

            List<Map<String, Object>> locationsWithStats = managedLocations.stream()
                    .map(location -> {
                        Map<String, Object> locationData = new HashMap<>();
                        locationData.put("id", location.getId());
                        locationData.put("name", location.getName());
                        locationData.put("address", location.getAddress());
                        locationData.put("type", location.getType());
                        locationData.put("description", location.getDescription());
                        locationData.put("imageUrl", location.getImageUrl());

                        Double avgRating = reviewService.getAverageRatingForLocation(location.getId());
                        Long totalReviews = reviewService.getTotalReviewsForLocation(location.getId());

                        locationData.put("averageRating", avgRating != null ? avgRating : 0.0);
                        locationData.put("totalReviews", totalReviews != null ? totalReviews : 0L);

                        List<LocationReview> recentReviews = reviewService.findByLocationId(location.getId())
                                .stream()
                                .filter(r -> !r.getIsDeleted()) // Ne prikazuj obrisane
                                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                                .limit(3)
                                .collect(Collectors.toList());

                        locationData.put("recentReviews", recentReviews);

                        return locationData;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("manager", Map.of(
                    "id", manager.getId(),
                    "fullName", manager.getFullName(),
                    "email", manager.getEmail()
            ));
            response.put("locations", locationsWithStats);
            response.put("totalLocations", locationsWithStats.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}