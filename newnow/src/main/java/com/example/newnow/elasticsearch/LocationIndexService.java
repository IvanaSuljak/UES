package com.example.newnow.elasticsearch;

import com.example.newnow.model.Location;
import com.example.newnow.model.LocationReview;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.repository.LocationReviewRepository;
import com.example.newnow.service.MinioService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class LocationIndexService {

    private static final Logger logger = LogManager.getLogger(LocationIndexService.class);

    @Autowired
    private LocationSearchRepository searchRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationReviewRepository reviewRepository;

    @Autowired
    private MinioService minioService;

    public void indexLocation(Location loc) {
        try {
            LocationDocument doc = buildDocument(loc);
            searchRepository.save(doc);
            logger.info("Lokacija indeksirana u ES: {}", loc.getName());
        } catch (Exception e) {
            logger.error("Greska pri indeksiranju lokacije {}: {}", loc.getName(), e.getMessage());
        }
    }

    public void reindexAll() {
        List<Location> locations = locationRepository.findAll();
        for (Location loc : locations) {
            indexLocation(loc);
        }
        logger.info("Sve lokacije reindeksirane: {} lokacija", locations.size());
    }

    public void removeFromIndex(Long locationId) {
        searchRepository.deleteById(String.valueOf(locationId));
    }

    private LocationDocument buildDocument(Location loc) {
        LocationDocument doc = new LocationDocument();
        doc.setId(String.valueOf(loc.getId()));
        doc.setName(loc.getName());
        doc.setDescription(loc.getDescription());
        doc.setAddress(loc.getAddress());
        doc.setType(loc.getType());
        doc.setImageUrl(loc.getImageUrl());

        // Izracunaj prosjecne ocjene iz recenzija
        List<LocationReview> reviews = reviewRepository.findByLocationId(loc.getId())
                .stream().filter(r -> !r.getIsDeleted()).toList();

        doc.setTotalReviews(reviews.size());

        if (!reviews.isEmpty()) {
            double avgPerf = reviews.stream()
                    .filter(r -> r.getPerformanceRating() != null)
                    .mapToInt(r -> r.getPerformanceRating()).average().orElse(0.0);
            double avgSound = reviews.stream()
                    .filter(r -> r.getSoundLightRating() != null)
                    .mapToInt(r -> r.getSoundLightRating()).average().orElse(0.0);
            double avgSpace = reviews.stream()
                    .filter(r -> r.getSpaceRating() != null)
                    .mapToInt(r -> r.getSpaceRating()).average().orElse(0.0);
            double avgOverall = reviews.stream()
                    .filter(r -> r.getOverallRating() != null)
                    .mapToInt(r -> r.getOverallRating()).average().orElse(0.0);

            doc.setAvgPerformance(avgPerf);
            doc.setAvgSoundLight(avgSound);
            doc.setAvgSpace(avgSpace);
            doc.setAvgOverall(avgOverall);

            double overallAvg = reviews.stream()
                    .mapToDouble(r -> r.getAverageRating()).average().orElse(0.0);
            doc.setAverageRating(overallAvg);
        }

        // PDF sadrzaj ako postoji
        if (loc.getPdfFileName() != null && !loc.getPdfFileName().isBlank()) {
            doc.setPdfFileName(loc.getPdfFileName());
            try {
                InputStream pdfStream = minioService.downloadFile(loc.getPdfFileName());
                byte[] pdfBytes = pdfStream.readAllBytes();
                PDDocument pdDoc = Loader.loadPDF(pdfBytes);
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(pdDoc);
                pdDoc.close();
                doc.setPdfContent(text);
                logger.info("PDF parsiran za lokaciju: {}", loc.getName());
            } catch (Exception e) {
                logger.warn("Nije moguce parsirati PDF za lokaciju {}: {}", loc.getName(), e.getMessage());
            }
        }

        return doc;
    }
}
