package com.example.newnow.service_impl;

import com.example.newnow.model.LocationReview;
import com.example.newnow.repository.LocationReviewRepository;
import com.example.newnow.service.LocationReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LocationReviewServiceImpl implements LocationReviewService {

    private final LocationReviewRepository reviewRepository;

    @Autowired
    public LocationReviewServiceImpl(LocationReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public LocationReview save(LocationReview review) {
        return reviewRepository.save(review);
    }

    @Override
    public Optional<LocationReview> findById(Long id) {
        return reviewRepository.findById(id);
    }

    @Override
    public List<LocationReview> findByLocationId(Long locationId) {
        return reviewRepository.findByLocationId(locationId);
    }

    @Override
    public List<LocationReview> findVisibleByLocationId(Long locationId) {
        return reviewRepository.findVisibleByLocationId(locationId);
    }

    @Override
    public List<LocationReview> findByLocationIdSortedByDateAsc(Long locationId) {
        return reviewRepository.findByLocationIdOrderByCreatedAtAsc(locationId);
    }

    @Override
    public List<LocationReview> findByLocationIdSortedByDateDesc(Long locationId) {
        return reviewRepository.findByLocationIdOrderByCreatedAtDesc(locationId);
    }

    @Override
    public List<LocationReview> findByLocationIdSortedByRatingAsc(Long locationId) {
        return reviewRepository.findByLocationId(locationId).stream()
                .sorted(Comparator.comparing(LocationReview::getAverageRating))
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationReview> findByLocationIdSortedByRatingDesc(Long locationId) {
        return reviewRepository.findByLocationId(locationId).stream()
                .sorted(Comparator.comparing(LocationReview::getAverageRating).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Double getAverageRatingForLocation(Long locationId) {
        Double avg = reviewRepository.getAverageRatingForLocation(locationId);
        return avg != null ? avg : 0.0;
    }

    @Override
    public Long getTotalReviewsForLocation(Long locationId) {
        return reviewRepository.getTotalReviewsForLocation(locationId);
    }

    // Sakrivanje/uklanjanje utisaka

    @Override
    public LocationReview hideReview(Long reviewId) {
        LocationReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Utisak nije pronađen"));
        review.setIsHidden(true);
        return reviewRepository.save(review);
    }

    @Override
    public LocationReview deleteReview(Long reviewId) {
        LocationReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Utisak nije pronađen"));
        review.setIsDeleted(true);
        return reviewRepository.save(review);
    }

    @Override
    public LocationReview unhideReview(Long reviewId) {
        LocationReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Utisak nije pronađen"));
        review.setIsHidden(false);
        return reviewRepository.save(review);
    }

    // Svi utisci po useru korisniku
    @Override
    public List<LocationReview> findByUserId(Long userId) {
        return reviewRepository.findAll().stream()
                .filter(r -> r.getUser() != null && r.getUser().getId().equals(userId))
                .collect(Collectors.toList());
    }
}