package com.example.newnow.controller;

import com.example.newnow.model.Event;
import com.example.newnow.model.LocationReview;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.service.EventService;
import com.example.newnow.service.LocationReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {

    @Autowired
    private LocationReviewService reviewService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private JwtUtil jwtUtil;

    //Sve recenzije za menadyerove događaje
    @GetMapping("/my-reviews")
    public ResponseEntity<?> getMyReviews(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User manager = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            // Proveri da li je MANAGER
            if (manager.getRole() != Role.MANAGER) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo menadžeri mogu pristupiti ovoj ruti!"
                ));
            }

            List<Long> managedLocationIds = locationRepository.findAll().stream()
                    .filter(loc -> loc.getManager() != null &&
                            loc.getManager().getId().equals(manager.getId()))
                    .map(loc -> loc.getId())
                    .collect(Collectors.toList());

            if (managedLocationIds.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<Long> eventIds = new ArrayList<>();
            for (Long locationId : managedLocationIds) {
                List<Event> events = eventService.findByLocationId(locationId);
                eventIds.addAll(events.stream().map(Event::getId).collect(Collectors.toList()));
            }

            List<LocationReview> allReviews = new ArrayList<>();
            for (Long locationId : managedLocationIds) {
                allReviews.addAll(reviewService.findByLocationId(locationId));
            }

            List<Map<String, Object>> formattedReviews = allReviews.stream()
                    .filter(review -> !review.getIsDeleted()) // Ne prikazuj obrisane
                    .map(review -> {
                        Map<String, Object> reviewData = new HashMap<>();
                        reviewData.put("id", review.getId());
                        reviewData.put("rating", review.getAverageRating());
                        reviewData.put("comment", review.getComment());
                        reviewData.put("createdAt", review.getCreatedAt());
                        reviewData.put("hidden", review.getIsHidden());

                        // User info
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("id", review.getUser().getId());
                        userData.put("fullName", review.getUser().getFullName());
                        reviewData.put("user", userData);

                        // Event info
                        if (review.getEvent() != null) {
                            Map<String, Object> eventData = new HashMap<>();
                            eventData.put("id", review.getEvent().getId());
                            eventData.put("title", review.getEvent().getTitle());
                            reviewData.put("event", eventData);
                        }

                        return reviewData;
                    })
                    .sorted((a, b) -> ((java.time.LocalDateTime) b.get("createdAt"))
                            .compareTo((java.time.LocalDateTime) a.get("createdAt")))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(formattedReviews);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    //Odgovori na recenziju
    @PostMapping("/{id}/reply")
    public ResponseEntity<?> replyToReview(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String token
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User manager = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            LocationReview review = reviewService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Recenzija nije pronađena"));

            // Proveri da li je menadžer ove lokacije
            if (manager.getRole() != Role.MANAGER ||
                    review.getLocation().getManager() == null ||
                    !review.getLocation().getManager().getId().equals(manager.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo menadžer lokacije može odgovoriti na recenziju!"
                ));
            }

            String reply = body.get("reply");
            if (reply == null || reply.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Odgovor ne može biti prazan!"));
            }



            return ResponseEntity.ok(Map.of(
                    "message", "Odgovor poslat ✅",
                    "reply", reply
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 🟢 M2: SAKRIJ utisak (samo menadžer lokacije)
    @PutMapping("/{id}/hide")
    public ResponseEntity<?> hideReview(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            LocationReview review = reviewService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Utisak nije pronađen"));

            if (user.getRole() != Role.MANAGER ||
                    review.getLocation().getManager() == null ||
                    !review.getLocation().getManager().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo menadžer lokacije može sakrivati utiske!"
                ));
            }

            reviewService.hideReview(id);
            return ResponseEntity.ok(Map.of("message", "Utisak sakriven ✅"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            LocationReview review = reviewService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Utisak nije pronađen"));

            if (user.getRole() != Role.MANAGER ||
                    review.getLocation().getManager() == null ||
                    !review.getLocation().getManager().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo menadžer lokacije može ukloniti utiske!"
                ));
            }

            reviewService.deleteReview(id);
            return ResponseEntity.ok(Map.of("message", "Utisak uklonjen ✅ (ocena se ne računa)"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/unhide")
    public ResponseEntity<?> unhideReview(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            LocationReview review = reviewService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Utisak nije pronađen"));

            if (user.getRole() != Role.MANAGER ||
                    review.getLocation().getManager() == null ||
                    !review.getLocation().getManager().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Samo menadžer lokacije može prikazati utiske!"
                ));
            }

            reviewService.unhideReview(id);
            return ResponseEntity.ok(Map.of("message", "Utisak prikazan ✅"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}