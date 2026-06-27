package com.example.newnow.controller;

import com.example.newnow.model.AccountRequest;
import com.example.newnow.model.RequestStatus;
import com.example.newnow.model.User;
import com.example.newnow.model.Role;
import com.example.newnow.model.Event;
import com.example.newnow.repository.AccountRequestRepository;
import com.example.newnow.repository.EventRepository;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.util.EventTypeUtil;
import com.example.newnow.security.JwtUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LogManager.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRequestRepository accountRequestRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EventRepository eventRepository;

    /**
     * Administrator sistema je predefinisan korisnik — kreira se pri prvom pokretanju.
     */
    @Bean
    public CommandLineRunner initAdmin() {
        return args -> {
            if (userRepository.findByEmail("admin@newnow.com").isEmpty()) {
                User admin = new User();
                admin.setEmail("admin@newnow.com");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setFullName("System Administrator");
                admin.setRole(Role.ADMIN);
                admin.setEnabled(true);

                userRepository.save(admin);
                logger.info("Inicijalni admin kreiran: admin@newnow.com / admin123");
            } else {
                logger.info("Admin korisnik već postoji.");
            }

            // Normalizacija tipova događaja: concert/Koncert → Concert
            int normalized = 0;
            for (Event event : eventRepository.findAll()) {
                String normalizedType = EventTypeUtil.normalize(event.getType());
                if (event.getType() != null && !event.getType().equals(normalizedType)) {
                    event.setType(normalizedType);
                    eventRepository.save(event);
                    normalized++;
                }
            }
            if (normalized > 0) {
                logger.info("Normalizovano {} tipova događaja na 'Concert'", normalized);
            }
        };
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok("Auth radi ✅");
    }

    /**
     * K2 — Prijava na sistem. JWT token se vraća samo ako je nalog enabled (odobren).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email i lozinka su obavezni."));
        }

        logger.info("K2 — login pokušaj: {}", email);

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            if (!passwordEncoder.matches(password, user.getPassword())) {
                logger.warn("K2 — pogrešna lozinka: {}", email);
                return ResponseEntity.status(401).body(Map.of("error", "Pogrešan email ili lozinka."));
            }

            if (!user.isEnabled()) {
                logger.warn("K2 — nalog nije aktiviran: {}", email);
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Nalog nije aktiviran. Sačekajte odobrenje administratora."));
            }

            String token = jwtUtil.generateToken(email);
            logger.info("K2 — uspešan login: {} ({})", email, user.getRole());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "role", user.getRole().toString(),
                    "email", user.getEmail(),
                    "fullName", user.getFullName()
            ));
        }

        // Nema User — proveri AccountRequest SAMO posle provere lozinke
        Optional<AccountRequest> requestOpt = accountRequestRepository.findByEmail(email);
        if (requestOpt.isPresent()) {
            AccountRequest ar = requestOpt.get();
            if (!passwordEncoder.matches(password, ar.getPassword())) {
                logger.warn("K2 — pogrešna lozinka (zahtev): {}", email);
                return ResponseEntity.status(401).body(Map.of("error", "Pogrešan email ili lozinka."));
            }
            RequestStatus status = ar.getStatus();
            if (status == RequestStatus.PENDING) {
                logger.warn("K2 — login pre odobrenja zahteva: {}", email);
                return ResponseEntity.status(401).body(Map.of(
                        "error", "Vaš zahtev za registraciju je na čekanju. Sačekajte odobrenje administratora."
                ));
            }
            if (status == RequestStatus.REJECTED) {
                logger.warn("K2 — login posle odbijenog zahteva: {}", email);
                return ResponseEntity.status(401).body(Map.of(
                        "error", "Vaš zahtev za registraciju je odbijen. Kontaktirajte administratora."
                ));
            }
        }

        logger.warn("K2 — korisnik ne postoji: {}", email);
        return ResponseEntity.status(401).body(Map.of("error", "Pogrešan email ili lozinka."));
    }

    /**
     * K2 — Odjava (stateless JWT: klijent briše token lokalno).
     * Endpoint služi za logovanje događaja; token se ne čuva na serveru.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String email = jwtUtil.extractUsername(authHeader.substring(7));
            logger.info("K2 — odjava korisnika: {}", email);
        }
        return ResponseEntity.ok(Map.of("message", "Uspešno ste se odjavili."));
    }
}
