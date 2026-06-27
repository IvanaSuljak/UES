package com.example.newnow.controller;

import com.example.newnow.elasticsearch.LocationIndexService;
import com.example.newnow.model.Location;
import com.example.newnow.model.LocationReview;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.EventRepository;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.service.EventService;
import com.example.newnow.service.LocationReviewService;
import com.example.newnow.service.LocationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/locations")
@CrossOrigin(origins = "*")
public class LocationController {

    private static final Logger logger = LogManager.getLogger(LocationController.class);

    private final LocationService locationService;
    private final UserRepository userRepository;

    @Autowired
    private LocationReviewService reviewService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private LocationIndexService locationIndexService;

    @Autowired
    public LocationController(LocationService locationService, UserRepository userRepository) {
        this.locationService = locationService;
        this.userRepository = userRepository;
    }

    // ─────────────────── HELPERS ───────────────────

    private User extractUser(String authHeader) {
        String email = jwtUtil.extractUsername(authHeader.substring(7));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));
    }

    private Map<String, Object> locationWithRating(Location loc) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", loc.getId());
        m.put("name", loc.getName());
        m.put("address", loc.getAddress());
        m.put("type", loc.getType());
        m.put("description", loc.getDescription());
        m.put("imageUrl", loc.getImageUrl());
        m.put("pdfFileName", loc.getPdfFileName());
        m.put("manager", loc.getManager() != null ? Map.of(
                "id", loc.getManager().getId(),
                "fullName", loc.getManager().getFullName()
        ) : null);
        Double avg = reviewService.getAverageRatingForLocation(loc.getId());
        Long total = reviewService.getTotalReviewsForLocation(loc.getId());
        m.put("averageRating", avg != null ? avg : 0.0);
        m.put("totalReviews", total != null ? total : 0L);
        return m;
    }

    // ─────────────────── K3 / K6 — READ ───────────────────

    /**
     * K3 — lista svih mesta sa prosečnom ocenom.
     * K6 — opciona pretraga: name, address, type.
     */
    @GetMapping
    public ResponseEntity<?> getAllLocations(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String type
    ) {
        List<Location> locations;
        boolean hasFilter = (name != null && !name.isBlank())
                || (address != null && !address.isBlank())
                || (type != null && !type.isBlank());

        if (hasFilter) {
            locations = locationService.searchLocations(
                    name != null && !name.isBlank() ? name : null,
                    address != null && !address.isBlank() ? address : null,
                    type != null && !type.isBlank() ? type : null
            );
        } else {
            locations = locationService.findAllValid();
        }

        List<Map<String, Object>> result = locations.stream()
                .map(this::locationWithRating)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getLocationById(@PathVariable Long id) {
        return locationService.findById(id)
                .map(loc -> ResponseEntity.ok((Object) locationWithRating(loc)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** K3 — detalji mesta: predstojeći događaji + prosečna ocena. */
    @GetMapping("/{id}/details")
    public ResponseEntity<?> getLocationDetails(@PathVariable Long id) {
        try {
            Location location = locationService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Mesto nije pronađeno"));

            List<Map<String, Object>> upcomingEvents = location.getEvents().stream()
                    .filter(e -> e.getDateTime().isAfter(LocalDateTime.now()))
                    .map(e -> {
                        Map<String, Object> ev = new HashMap<>();
                        ev.put("id", e.getId());
                        ev.put("title", e.getTitle());
                        ev.put("description", e.getDescription());
                        ev.put("dateTime", e.getDateTime().toString());
                        ev.put("price", e.getPrice());
                        ev.put("type", e.getType());
                        ev.put("imageUrl", e.getImageUrl());
                        ev.put("isRegular", e.getIsRegular());
                        ev.put("locationAddress", location.getAddress());
                        return ev;
                    })
                    .sorted(java.util.Comparator.comparing(m -> (String) m.get("dateTime")))
                    .collect(Collectors.toList());

            Map<String, Object> response = locationWithRating(location);
            response.put("upcomingEvents", upcomingEvents);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────── K3 / A2 — WRITE (admin/menadžer) ───────────────────

    /**
     * K3 — Admin kreira novo mesto.
     * Obavezna polja: naziv, adresa, tip, opis, imageUrl (slika).
     */
    @PostMapping
    public ResponseEntity<?> createLocation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        try {
            User user = extractUser(authHeader);
            if (user.getRole() != Role.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo administrator može kreirati mesto."));
            }

            String nameVal = (String) body.get("name");
            String addressVal = (String) body.get("address");
            String typeVal = (String) body.get("type");
            String descVal = (String) body.get("description");
            String imageUrl = (String) body.get("imageUrl");

            if (isBlank(nameVal) || isBlank(addressVal) || isBlank(typeVal)
                    || isBlank(descVal) || isBlank(imageUrl)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Naziv, adresa, tip mesta, opis i slika su obavezni."
                ));
            }

            Location loc = new Location();
            loc.setName(nameVal);
            loc.setAddress(addressVal);
            loc.setType(typeVal);
            loc.setDescription(descVal);
            loc.setImageUrl(imageUrl);

            Location saved = locationService.save(loc);
            locationIndexService.indexLocation(saved);
            logger.info("K3 — admin {} kreirao mesto: {}", user.getEmail(), saved.getName());
            return ResponseEntity.ok(locationWithRating(saved));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * K3 / M1 — Ažuriranje mesta.
     * Admin: može menjati sve.
     * Menadžer: može menjati samo adresu, tip i opis svog mesta.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateLocation(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        try {
            User user = extractUser(authHeader);
            Location location = locationService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Mesto nije pronađeno"));

            boolean isAdmin = user.getRole() == Role.ADMIN;
            boolean isManagerOfThis = user.getRole() == Role.MANAGER
                    && location.getManager() != null
                    && location.getManager().getId().equals(user.getId());

            if (!isAdmin && !isManagerOfThis) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Niste ovlašćeni za izmenu ovog mesta."
                ));
            }

            if (isAdmin) {
                if (body.containsKey("name") && !isBlank((String) body.get("name")))
                    location.setName((String) body.get("name"));
                if (body.containsKey("imageUrl"))
                    location.setImageUrl((String) body.get("imageUrl"));
            }

            // I admin i menadžer mogu menjati ova tri polja
            if (body.containsKey("address") && !isBlank((String) body.get("address")))
                location.setAddress((String) body.get("address"));
            if (body.containsKey("type") && !isBlank((String) body.get("type")))
                location.setType((String) body.get("type"));
            if (body.containsKey("description"))
                location.setDescription((String) body.get("description"));

            Location updated = locationService.save(location);
            locationIndexService.indexLocation(updated);
            logger.info("K3 — {} ({}) azurirao mesto: {}", user.getEmail(), user.getRole(), updated.getName());
            return ResponseEntity.ok(locationWithRating(updated));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * K3 — Admin briše mesto.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLocation(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            User user = extractUser(authHeader);
            if (user.getRole() != Role.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo administrator može brisati mesta."));
            }
            locationService.deleteById(id);
            locationIndexService.removeFromIndex(id);
            logger.info("K3 — admin {} obrisao mesto id={}", user.getEmail(), id);
            return ResponseEntity.ok(Map.of("message", "Mesto obrisano."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────── A2 — upravljanje menadžerima ───────────────────

    /**
     * A2 — Admin dodeljuje menadžera mestu.
     * Korisnik dobija ulogu MANAGER; ako je već MANAGER drugog mesta, to ostaje.
     */
    @PutMapping("/{locationId}/assign-manager")
    public ResponseEntity<?> assignManager(
            @PathVariable Long locationId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Long> payload
    ) {
        try {
            User admin = extractUser(authHeader);
            if (admin.getRole() != Role.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo administrator može dodavati menadžere."));
            }

            Long managerId = payload.get("managerId");
            if (managerId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "managerId je obavezan."));
            }

            Location location = locationService.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Mesto nije pronađeno"));
            User manager = userRepository.findById(managerId)
                    .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));

            manager.setRole(Role.MANAGER);
            userRepository.save(manager);
            location.setManager(manager);
            locationService.save(location);

            logger.info("A2 — {} dodelio menadzera {} mestu {}", admin.getEmail(), manager.getEmail(), location.getName());
            return ResponseEntity.ok(Map.of("message", "Menadžer dodeljen."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * A2 — Admin uklanja menadžera sa mesta.
     * Korisnik se vraća na ulogu USER.
     */
    @DeleteMapping("/{locationId}/remove-manager")
    public ResponseEntity<?> removeManager(
            @PathVariable Long locationId,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            User admin = extractUser(authHeader);
            if (admin.getRole() != Role.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo administrator može uklanjati menadžere."));
            }

            Location location = locationService.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Mesto nije pronađeno"));

            User manager = location.getManager();
            if (manager != null) {
                long otherLocations = locationRepository.countByManagerIdAndIdNot(manager.getId(), locationId);
                if (otherLocations == 0 && manager.getRole() == Role.MANAGER) {
                    manager.setRole(Role.USER);
                    userRepository.save(manager);
                }
                logger.info("A2 — {} uklonio menadzera {} sa mesta {}", admin.getEmail(), manager.getEmail(), location.getName());
            }
            location.setManager(null);
            locationService.save(location);

            return ResponseEntity.ok(Map.of("message", "Menadžer uklonjen."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** Menadžer dohvata svoje mesto. */
    @GetMapping("/my-location")
    public ResponseEntity<?> getMyLocation(@RequestHeader("Authorization") String authHeader) {
        try {
            User manager = extractUser(authHeader);
            if (manager.getRole() != Role.MANAGER) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo menadžeri mogu pristupiti ovoj ruti."));
            }
            Location location = locationService.findByManager(manager);
            if (location == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Niste dodeljeni ni jednoj lokaciji."));
            }
            return ResponseEntity.ok(locationWithRating(location));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────── K5 — utisci ───────────────────

    /** K5 — korisnik ostavlja utisak na mesto. */
    @PostMapping("/{id}/reviews")
    public ResponseEntity<?> addReview(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> reviewData
    ) {
        try {
            User user = extractUser(authHeader);
            Location location = locationService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Mesto nije pronađeno"));

            com.example.newnow.model.LocationReview review = new com.example.newnow.model.LocationReview();

            if (reviewData.containsKey("performanceRating") && reviewData.get("performanceRating") != null)
                review.setPerformanceRating(validateRating(reviewData.get("performanceRating"), "Nastup"));
            if (reviewData.containsKey("soundLightRating") && reviewData.get("soundLightRating") != null)
                review.setSoundLightRating(validateRating(reviewData.get("soundLightRating"), "Zvuk i svetlo"));
            if (reviewData.containsKey("spaceRating") && reviewData.get("spaceRating") != null)
                review.setSpaceRating(validateRating(reviewData.get("spaceRating"), "Prostor"));
            if (reviewData.containsKey("overallRating") && reviewData.get("overallRating") != null)
                review.setOverallRating(validateRating(reviewData.get("overallRating"), "Ukupan utisak"));

            review.setComment(reviewData.get("comment") != null ? ((String) reviewData.get("comment")).trim() : null);

            if (reviewData.containsKey("eventId") && reviewData.get("eventId") != null) {
                Long eventId = ((Number) reviewData.get("eventId")).longValue();
                com.example.newnow.model.Event event = eventRepository.findById(eventId)
                        .orElseThrow(() -> new RuntimeException("Događaj nije pronađen"));

                if (event.getIsRegular() == null || !event.getIsRegular())
                    return ResponseEntity.badRequest().body(Map.of("error", "Utisak se može ostaviti samo za redovne događaje!"));
                if (event.getDateTime().isAfter(LocalDateTime.now()))
                    return ResponseEntity.badRequest().body(Map.of("error", "Utisak se može ostaviti samo nakon što se događaj održao!"));
                if (!event.getLocation().getId().equals(location.getId()))
                    return ResponseEntity.badRequest().body(Map.of("error", "Događaj nije na ovoj lokaciji!"));

                Long occurrenceCount = eventService.countEventOccurrences(eventId);
                review.setEventOccurrenceCount(occurrenceCount.intValue());
                review.setEvent(event);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Izbor događaja je obavezan za utisak."));
            }

            review.setUser(user);
            review.setLocation(location);

            com.example.newnow.model.LocationReview saved = reviewService.save(review);
            locationIndexService.indexLocation(location);
            logger.info("K5 — {} ostavio utisak na mestu {}", user.getEmail(), location.getName());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** K7 — utisci za mesto (vidljivi), sa opcionalnim sortiranjem. */
    @GetMapping("/{id}/reviews")
    public ResponseEntity<?> getReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String order
    ) {
        try {
            List<com.example.newnow.model.LocationReview> reviews;
            boolean asc = "asc".equalsIgnoreCase(order);

            if ("rating".equalsIgnoreCase(sortBy)) {
                reviews = asc
                        ? reviewService.findByLocationIdSortedByRatingAsc(id)
                        : reviewService.findByLocationIdSortedByRatingDesc(id);
                // Filtriramo skrivene/obrisane
                reviews = reviews.stream()
                        .filter(r -> !r.getIsDeleted() && !r.getIsHidden())
                        .collect(Collectors.toList());
            } else {
                reviews = asc
                        ? reviewService.findByLocationIdSortedByDateAsc(id)
                        : reviewService.findByLocationIdSortedByDateDesc(id);
                reviews = reviews.stream()
                        .filter(r -> !r.getIsDeleted() && !r.getIsHidden())
                        .collect(Collectors.toList());
            }

            List<Map<String, Object>> formatted = reviews.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("user", r.getUser());
                m.put("comment", r.getComment());
                m.put("createdAt", r.getCreatedAt());
                m.put("averageRating", r.getAverageRating());
                m.put("performanceRating", r.getPerformanceRating());
                m.put("soundLightRating", r.getSoundLightRating());
                m.put("spaceRating", r.getSpaceRating());
                m.put("overallRating", r.getOverallRating());
                m.put("event", r.getEvent());
                m.put("eventOccurrenceCount", r.getEventOccurrenceCount());
                return m;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(formatted);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** M2 — svi utisci za mesto (uključujući skrivene), samo za menadžera. */
    @GetMapping("/{id}/reviews/all")
    public ResponseEntity<?> getAllReviews(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            User user = extractUser(authHeader);
            Location location = locationService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Mesto nije pronađeno"));

            if (user.getRole() != Role.MANAGER
                    || location.getManager() == null
                    || !location.getManager().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo menadžer lokacije može videti sve utiske."));
            }

            return ResponseEntity.ok(reviewService.findByLocationId(id));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────── UTILS ───────────────────

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private int validateRating(Object value, String label) {
        int v = ((Number) value).intValue();
        if (v < 1 || v > 10) {
            throw new IllegalArgumentException(label + " ocena mora biti između 1 i 10.");
        }
        return v;
    }
}
