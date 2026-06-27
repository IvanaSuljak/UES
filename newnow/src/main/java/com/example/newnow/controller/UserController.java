package com.example.newnow.controller;

import com.example.newnow.model.Location;
import com.example.newnow.model.LocationReview;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.service.LocationReviewService;
import com.example.newnow.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LogManager.getLogger(UserController.class);

    private final UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LocationReviewService reviewService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** A2 — Admin lista korisnika za dodelu menadžera (bez lozinke). */
    @GetMapping
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String token) {
        try {
            User admin = extractUser(token);
            if (admin.getRole() != Role.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo administrator može pristupiti listi korisnika."));
            }
            List<Map<String, Object>> safe = userService.findAll().stream()
                    .map(u -> Map.<String, Object>of(
                            "id", u.getId(),
                            "email", u.getEmail(),
                            "fullName", u.getFullName(),
                            "role", u.getRole().name()
                    ))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(safe);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Neautorizovan pristup."));
        }
    }

    private User extractUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Nedostaje Authorization header.");
        }
        String email = jwtUtil.extractUsername(authHeader.substring(7));
        return userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen."));
    }

    //Promena lozinke
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> payload
    ) {
        try {
            String jwt = token.substring(7); // Ukloni "Bearer "
            String email = jwtUtil.extractUsername(jwt);

            String oldPassword = payload.get("oldPassword");
            String newPassword = payload.get("newPassword");

            if (oldPassword == null || newPassword == null) {
                return ResponseEntity.badRequest().body("Stara i nova lozinka su obavezne");
            }

            boolean success = userService.changePassword(email, oldPassword, newPassword);

            if (success) {
                return ResponseEntity.ok(Map.of("message", "Lozinka uspešno promenjena! Email notifikacija je poslata."));
            } else {
                return ResponseEntity.status(400).body("Stara lozinka nije tačna");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Greška: " + e.getMessage());
        }
    }
    //profil trenutno ulogovanog
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String token) {
        try {
            // Izvuci email iz JWT tokena
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            Map<String, Object> response = new HashMap<>();

            response.put("id", user.getId());
            response.put("email", user.getEmail());
            response.put("fullName", user.getFullName());
            response.put("address", user.getAddress());
            response.put("role", user.getRole());
            response.put("profileImage", user.getProfileImage());

            List<LocationReview> userReviews = reviewService.findByUserId(user.getId()).stream()
                    .filter(r -> !r.getIsDeleted()) // Ne prikazuj obrisane
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(Collectors.toList());

            response.put("reviews", userReviews);
            response.put("totalReviews", userReviews.size());

            if (user.getRole() == Role.MANAGER) {
                List<Location> managedLocations = locationRepository.findAll().stream()
                        .filter(loc -> loc.getManager() != null &&
                                loc.getManager().getId().equals(user.getId()))
                        .collect(Collectors.toList());

                response.put("managedLocations", managedLocations);
                response.put("totalManagedLocations", managedLocations.size());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> payload
    ) {
        try {
            String jwt = token.substring(7);
            String email = jwtUtil.extractUsername(jwt);
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            if (payload.containsKey("fullName") && payload.get("fullName") != null) {
                user.setFullName(payload.get("fullName"));
            }
            if (payload.containsKey("address")) {
                user.setAddress(payload.get("address"));
            }
            if (payload.containsKey("profileImage")) {
                user.setProfileImage(payload.get("profileImage"));
            }

            User updated = userService.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Profil uspešno ažuriran ✅",
                    "user", Map.of(
                            "id", updated.getId(),
                            "email", updated.getEmail(),
                            "fullName", updated.getFullName(),
                            "address", updated.getAddress()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}