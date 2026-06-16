package com.example.newnow.controller;

import com.example.newnow.model.AccountRequest;
import com.example.newnow.model.RequestStatus;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.AccountRequestRepository;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.service.AccountRequestService;
import com.example.newnow.service.EmailService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account-requests")
@CrossOrigin(origins = "*")
public class AccountRequestController {

    private static final Logger logger = LogManager.getLogger(AccountRequestController.class);

    @Autowired
    private AccountRequestService accountRequestService;

    @Autowired
    private AccountRequestRepository accountRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    /**
     * K1 — Neregistrovan korisnik šalje zahtev za registraciju.
     * Korisnik još ne postoji u tabeli users — čeka admin odobrenje (A1).
     */
    @PostMapping
    public ResponseEntity<?> createRequest(@RequestBody AccountRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()
                || request.getFullName() == null || request.getFullName().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ime, email i lozinka su obavezni."));
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            logger.warn("K1 odbijen — email već postoji kao korisnik: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email već postoji u sistemu!"));
        }

        if (accountRequestRepository.existsByEmailAndStatus(request.getEmail(), RequestStatus.PENDING)) {
            logger.warn("K1 odbijen — već postoji pending zahtev: {}", request.getEmail());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Zahtev za ovaj email je već poslat i čeka odobrenje."));
        }

        request.setPassword(passwordEncoder.encode(request.getPassword()));
        request.setStatus(RequestStatus.PENDING);

        accountRequestService.save(request);
        logger.info("K1 — novi zahtev za registraciju: {} ({})", request.getEmail(), request.getFullName());

        return ResponseEntity.ok(Map.of(
                "message", "Zahtev za registraciju uspešno poslat! Administrator će ga obraditi."
        ));
    }

    /**
     * A1 — Admin vidi sve zahteve.
     */
    @GetMapping
    public List<AccountRequest> getAllRequests() {
        return accountRequestService.findAll();
    }

    /**
     * A1 — Admin vidi pending zahteve.
     */
    @GetMapping("/pending")
    public List<AccountRequest> getPendingRequests() {
        return accountRequestService.findByStatus(RequestStatus.PENDING);
    }

    /**
     * A1 — Admin prihvata zahtev → kreira User sa enabled=true → šalje email.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveRequest(@PathVariable Long id) {
        AccountRequest request = accountRequestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Zahtev nije pronađen"));

        if (request.getStatus() != RequestStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Zahtev je već obrađen."));
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Korisnik sa ovim emailom već postoji."));
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPassword(request.getPassword());
        user.setRole(Role.USER);
        user.setEnabled(true);

        userRepository.save(user);

        request.setStatus(RequestStatus.APPROVED);
        accountRequestService.save(request);

        emailService.sendAccountApprovedEmail(request.getEmail(), request.getFullName());
        logger.info("A1 — zahtev odobren: {} → kreiran korisnik", request.getEmail());

        return ResponseEntity.ok(Map.of("message", "Zahtev odobren ✅"));
    }

    /**
     * A1 — Admin odbija zahtev → šalje email, korisnik se NE kreira.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectRequest(@PathVariable Long id) {
        AccountRequest request = accountRequestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Zahtev nije pronađen"));

        if (request.getStatus() != RequestStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Zahtev je već obrađen."));
        }

        request.setStatus(RequestStatus.REJECTED);
        accountRequestService.save(request);

        emailService.sendAccountRejectedEmail(request.getEmail(), request.getFullName());
        logger.info("A1 — zahtev odbijen: {}", request.getEmail());

        return ResponseEntity.ok(Map.of("message", "Zahtev odbijen ❌"));
    }
}
