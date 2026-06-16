package com.example.newnow.controller;

import com.example.newnow.model.Location;
import com.example.newnow.model.LocationReview;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
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

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/{email}")
    public Optional<User> getUserByEmail(@PathVariable String email) {
        return userService.findByEmail(email);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
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