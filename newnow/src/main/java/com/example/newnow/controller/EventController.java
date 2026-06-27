package com.example.newnow.controller;

import com.example.newnow.model.Event;
import com.example.newnow.model.Location;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.service.EventService;
import com.example.newnow.util.EventTypeUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    private static final Logger logger = LogManager.getLogger(EventController.class);

    private final EventService eventService;
    private final LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    public EventController(EventService eventService, LocationRepository locationRepository) {
        this.eventService = eventService;
        this.locationRepository = locationRepository;
    }

    // ─────────────────── HELPER ───────────────────

    private User extractUser(String authHeader) {
        String email = jwtUtil.extractUsername(authHeader.substring(7));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));
    }

    /**
     * Proverava da li je korisnik menadžer konkretne lokacije.
     */
    private boolean isManagerOf(User user, Location location) {
        return user.getRole() == Role.MANAGER
                && location.getManager() != null
                && location.getManager().getId().equals(user.getId());
    }

    // ─────────────────── READ ───────────────────

    /** K6 — svi događaji, opcionalni filteri. */
    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        return ResponseEntity.ok(eventService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable Long id) {
        Optional<Event> ev = eventService.findById(id);
        return ev.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/byLocation/{locationId}")
    public ResponseEntity<List<Event>> getEventsByLocation(@PathVariable Long locationId) {
        return ResponseEntity.ok(eventService.findByLocationId(locationId));
    }

    /** K6 — današnji događaji. */
    @GetMapping("/today")
    public ResponseEntity<List<Event>> getTodayEvents() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = LocalDate.now().plusDays(1).atStartOfDay();
        return ResponseEntity.ok(eventService.findTodayEvents(start, end));
    }

    /** K6 — filter po proizvoljnom datumu (prošlost ili budućnost). */
    @GetMapping("/filter/date")
    public ResponseEntity<List<Event>> getEventsByDate(@RequestParam String date) {
        LocalDate localDate = LocalDate.parse(date); // yyyy-MM-dd
        return ResponseEntity.ok(eventService.findByDate(localDate));
    }

    /** K6 — pretraga po tipu. */
    @GetMapping("/search/type")
    public ResponseEntity<List<Event>> searchByType(@RequestParam String type) {
        return ResponseEntity.ok(eventService.searchByType(type));
    }

    /** K6 — pretraga po imenu mesta. */
    @GetMapping("/search/location")
    public ResponseEntity<List<Event>> searchByLocationName(@RequestParam String locationName) {
        return ResponseEntity.ok(eventService.searchByLocationName(locationName));
    }

    /** K6 — pretraga po adresi mesta. */
    @GetMapping("/search/address")
    public ResponseEntity<List<Event>> searchByAddress(@RequestParam String address) {
        return ResponseEntity.ok(eventService.searchByAddress(address));
    }

    /** K6 — filter po ceni. */
    @GetMapping("/filter/price")
    public ResponseEntity<List<Event>> filterByPrice(
            @RequestParam(defaultValue = "0.0") Double minPrice,
            @RequestParam(defaultValue = "10000.0") Double maxPrice
    ) {
        return ResponseEntity.ok(eventService.filterByPriceRange(minPrice, maxPrice));
    }

    /** K6 — besplatni događaji. */
    @GetMapping("/free")
    public ResponseEntity<List<Event>> getFreeEvents() {
        return ResponseEntity.ok(eventService.findFreeEvents());
    }

    // ─────────────────── K4 — WRITE (samo menadžer mesta) ───────────────────

    /**
     * K4 — Menadžer mesta kreira događaj.
     * Obavezna polja: title, locationId, type, dateTime, isRegular, imageUrl.
     * Cena: price (null = besplatan).
     */
    @PostMapping
    public ResponseEntity<?> createEvent(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        try {
            User user = extractUser(authHeader);

            if (user.getRole() != Role.MANAGER && user.getRole() != Role.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Samo menadžer mesta može kreirati događaj."));
            }

            Object locIdObj = body.get("locationId");
            if (locIdObj == null)
                return ResponseEntity.badRequest().body(Map.of("error", "locationId je obavezan."));

            Long locationId = Long.valueOf(locIdObj.toString());
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Lokacija nije pronađena"));

            if (user.getRole() == Role.MANAGER && !isManagerOf(user, location)) {
                return ResponseEntity.status(403).body(Map.of("error", "Možete dodavati događaje samo na sopstvenoj lokaciji."));
            }

            // Validacija obaveznih polja
            String title    = (String) body.get("title");
            String type     = (String) body.get("type");
            String dateTimeStr = (String) body.get("dateTime");
            Object isRegularObj = body.get("isRegular");
            String imageUrl = (String) body.get("imageUrl");

            if (isBlank(title) || isBlank(type) || isBlank(dateTimeStr)
                    || isRegularObj == null || isBlank(imageUrl)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Naziv, tip, datum, redovnost i slika su obavezni."
                ));
            }

            Event event = new Event();
            event.setTitle(title);
            event.setDescription((String) body.get("description"));
            event.setDateTime(LocalDateTime.parse(dateTimeStr));
            event.setType(EventTypeUtil.normalize(type));
            event.setIsRegular((Boolean) isRegularObj);
            event.setImageUrl(imageUrl);
            event.setLocation(location);

            if (body.get("price") != null) {
                Double price = Double.valueOf(body.get("price").toString());
                event.setPrice(price == 0.0 ? null : price);
            }

            Event saved = eventService.save(event);
            logger.info("K4 — {} kreirao dogadjaj '{}' na lokaciji {}", user.getEmail(), saved.getTitle(), location.getName());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * K4 / M1 — Menadžer mesta ažurira događaj.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        try {
            User user = extractUser(authHeader);
            Event existing = eventService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Događaj nije pronađen"));

            if (user.getRole() == Role.MANAGER && !isManagerOf(user, existing.getLocation())) {
                return ResponseEntity.status(403).body(Map.of("error", "Možete menjati samo događaje na sopstvenoj lokaciji."));
            }
            if (user.getRole() == Role.USER) {
                return ResponseEntity.status(403).body(Map.of("error", "Nemate pravo izmene događaja."));
            }

            if (body.containsKey("title") && !isBlank((String) body.get("title")))
                existing.setTitle((String) body.get("title"));
            if (body.containsKey("description"))
                existing.setDescription((String) body.get("description"));
            if (body.containsKey("dateTime") && body.get("dateTime") != null)
                existing.setDateTime(LocalDateTime.parse((String) body.get("dateTime")));
            if (body.containsKey("type") && !isBlank((String) body.get("type")))
                existing.setType(EventTypeUtil.normalize((String) body.get("type")));
            if (body.containsKey("isRegular") && body.get("isRegular") != null)
                existing.setIsRegular((Boolean) body.get("isRegular"));
            if (body.containsKey("imageUrl") && !isBlank((String) body.get("imageUrl")))
                existing.setImageUrl((String) body.get("imageUrl"));
            if (body.containsKey("price")) {
                if (body.get("price") == null) {
                    existing.setPrice(null);
                } else {
                    Double price = Double.valueOf(body.get("price").toString());
                    existing.setPrice(price == 0.0 ? null : price);
                }
            }

            Event updated = eventService.save(existing);
            logger.info("K4 — {} azurirao dogadjaj '{}'", user.getEmail(), updated.getTitle());
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * K4 — Menadžer mesta briše događaj.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            User user = extractUser(authHeader);
            Event existing = eventService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Događaj nije pronađen"));

            if (user.getRole() == Role.MANAGER && !isManagerOf(user, existing.getLocation())) {
                return ResponseEntity.status(403).body(Map.of("error", "Možete brisati samo događaje na sopstvenoj lokaciji."));
            }
            if (user.getRole() == Role.USER) {
                return ResponseEntity.status(403).body(Map.of("error", "Nemate pravo brisanja događaja."));
            }

            eventService.deleteById(id);
            logger.info("K4 — {} obrisao dogadjaj id={}", user.getEmail(), id);
            return ResponseEntity.ok(Map.of("message", "Događaj obrisan."));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────── UTILS ───────────────────

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
