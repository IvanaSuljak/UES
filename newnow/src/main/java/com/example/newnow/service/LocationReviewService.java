package com.example.newnow.service;

import com.example.newnow.model.LocationReview;
import java.util.List;
import java.util.Optional;

public interface LocationReviewService {
    LocationReview save(LocationReview review);
    Optional<LocationReview> findById(Long id);
    List<LocationReview> findByLocationId(Long locationId);
    List<LocationReview> findVisibleByLocationId(Long locationId);
    List<LocationReview> findByLocationIdSortedByDateAsc(Long locationId);
    List<LocationReview> findByLocationIdSortedByDateDesc(Long locationId);
    List<LocationReview> findByLocationIdSortedByRatingAsc(Long locationId);
    List<LocationReview> findByLocationIdSortedByRatingDesc(Long locationId);
    Double getAverageRatingForLocation(Long locationId);
    Long getTotalReviewsForLocation(Long locationId);

    LocationReview hideReview(Long reviewId);
    LocationReview deleteReview(Long reviewId);
    LocationReview unhideReview(Long reviewId);
    List<LocationReview> findByUserId(Long userId);
}