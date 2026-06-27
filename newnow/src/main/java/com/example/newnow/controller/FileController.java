package com.example.newnow.controller;

import com.example.newnow.model.Location;
import com.example.newnow.model.Role;
import com.example.newnow.model.User;
import com.example.newnow.repository.UserRepository;
import com.example.newnow.security.JwtUtil;
import com.example.newnow.service.MinioService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    private static final Logger logger = LogManager.getLogger(FileController.class);
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    @Autowired
    private MinioService minioService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /** Upload slike lokacije ili događaja u MinIO (K3/K4). */
    @PostMapping("/images")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token
    ) {
        try {
            User user = extractUser(token);
            if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
                return ResponseEntity.status(403).body(Map.of("error", "Nemate pravo uploada slika."));
            }

            String contentType = file.getContentType();
            if (contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Dozvoljene su samo slike (JPEG, PNG, GIF, WEBP)."));
            }

            String objectName = minioService.uploadFile(file, "images");
            logger.info("Slika uploadovana u MinIO: {}", objectName);
            return ResponseEntity.ok(Map.of(
                    "message", "Slika uspešno uploadovana",
                    "objectName", objectName,
                    "url", "/api/files/" + objectName
            ));
        } catch (Exception e) {
            logger.error("Greška pri uploadu slike: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** Preuzimanje fajla iz MinIO (slike i PDF). */
    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<?> downloadFile(
            @PathVariable String folder,
            @PathVariable String filename
    ) {
        try {
            String objectName = folder + "/" + filename;
            InputStream stream = minioService.downloadFile(objectName);
            MediaType mediaType = objectName.startsWith("images/")
                    ? MediaType.IMAGE_JPEG
                    : MediaType.APPLICATION_PDF;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private User extractUser(String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        String email = jwtUtil.extractUsername(jwt);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Korisnik nije pronađen"));
    }
}
