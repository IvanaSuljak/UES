package com.example.newnow.controller;

import com.example.newnow.elasticsearch.LocationIndexService;
import com.example.newnow.elasticsearch.LocationSearchService;
import com.example.newnow.model.Location;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.service.MinioService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = "*")
public class SearchController {

    private static final Logger logger = LogManager.getLogger(SearchController.class);

    @Autowired
    private LocationSearchService searchService;

    @Autowired
    private LocationIndexService indexService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private MinioService minioService;

    /**
     * S1 - Napredna pretraga mesta
     */
    @GetMapping("/locations")
    public ResponseEntity<?> searchLocations(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String pdf,
            @RequestParam(required = false) Integer reviewsMin,
            @RequestParam(required = false) Integer reviewsMax,
            @RequestParam(required = false) Double ratingMin,
            @RequestParam(required = false) Double ratingMax,
            @RequestParam(required = false) Double perfMin,
            @RequestParam(required = false) Double perfMax,
            @RequestParam(required = false) Double soundMin,
            @RequestParam(required = false) Double soundMax,
            @RequestParam(required = false) Double spaceMin,
            @RequestParam(required = false) Double spaceMax,
            @RequestParam(required = false) Double overallMin,
            @RequestParam(required = false) Double overallMax,
            @RequestParam(defaultValue = "AND") String operator,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder
    ) {
        try {
            List<Map<String, Object>> results = searchService.search(
                    name, description, pdf,
                    reviewsMin, reviewsMax,
                    ratingMin, ratingMax,
                    perfMin, perfMax,
                    soundMin, soundMax,
                    spaceMin, spaceMax,
                    overallMin, overallMax,
                    operator, sortBy, sortOrder
            );
            logger.info("Pretraga mesta - pronadjeno {} rezultata", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Greska pri pretrazi: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * S1 - More Like This pretraga
     */
    @GetMapping("/locations/{id}/similar")
    public ResponseEntity<?> similarLocations(@PathVariable String id) {
        try {
            List<Map<String, Object>> results = searchService.moreLikeThis(id);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload PDF za lokaciju
     */
    @PostMapping("/locations/{id}/pdf")
    public ResponseEntity<?> uploadPdf(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token
    ) {
        try {
            Location location = locationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Lokacija nije pronadjena"));

            if (!file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Samo PDF fajlovi su dozvoljeni!"));
            }

            String objectName = minioService.uploadFile(file, "pdfs");
            location.setPdfFileName(objectName);
            locationRepository.save(location);

            // Reindeksiramo lokaciju sa novim PDF sadrzajem
            indexService.indexLocation(location);

            logger.info("PDF uploadovan za lokaciju {}: {}", location.getName(), objectName);
            return ResponseEntity.ok(Map.of(
                    "message", "PDF uspjesno uploadovan",
                    "pdfFileName", objectName
            ));
        } catch (Exception e) {
            logger.error("Greska pri uploadu PDF-a: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Download PDF za lokaciju
     */
    @GetMapping("/locations/{id}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Long id) {
        try {
            Location location = locationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Lokacija nije pronadjena"));

            if (location.getPdfFileName() == null) {
                return ResponseEntity.notFound().build();
            }

            InputStream stream = minioService.downloadFile(location.getPdfFileName());
            String filename = "lokacija-" + id + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reindeksiranje svih lokacija (admin endpoint)
     */
    @PostMapping("/reindex")
    public ResponseEntity<?> reindexAll() {
        try {
            indexService.reindexAll();
            return ResponseEntity.ok(Map.of("message", "Sve lokacije su reindeksirane u Elasticsearch"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
