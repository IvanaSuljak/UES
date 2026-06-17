package com.example.newnow.repository;

import com.example.newnow.model.LocationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationReviewRepository extends JpaRepository<LocationReview, Long> {

    // Svi utisci za lokaciju
    @Query("SELECT lr FROM LocationReview lr WHERE lr.location.id = :locationId AND lr.isDeleted = false")
    List<LocationReview> findByLocationId(@Param("locationId") Long locationId);

    // Samo vidljivi utisci
    @Query("SELECT lr FROM LocationReview lr WHERE lr.location.id = :locationId AND lr.isDeleted = false AND lr.isHidden = false")
    List<LocationReview> findVisibleByLocationId(@Param("locationId") Long locationId);

    @Query("SELECT lr FROM LocationReview lr WHERE lr.location.id = :locationId AND lr.isDeleted = false ORDER BY lr.createdAt ASC")
    List<LocationReview> findByLocationIdOrderByCreatedAtAsc(@Param("locationId") Long locationId);

    @Query("SELECT lr FROM LocationReview lr WHERE lr.location.id = :locationId AND lr.isDeleted = false ORDER BY lr.createdAt DESC")
    List<LocationReview> findByLocationIdOrderByCreatedAtDesc(@Param("locationId") Long locationId);

    //  Prosecna ocena
    @Query("SELECT AVG((COALESCE(lr.performanceRating, 0) + COALESCE(lr.soundLightRating, 0) + " +
            "COALESCE(lr.spaceRating, 0) + COALESCE(lr.overallRating, 0)) / " +
            "NULLIF((CASE WHEN lr.performanceRating IS NOT NULL THEN 1 ELSE 0 END + " +
            "CASE WHEN lr.soundLightRating IS NOT NULL THEN 1 ELSE 0 END + " +
            "CASE WHEN lr.spaceRating IS NOT NULL THEN 1 ELSE 0 END + " +
            "CASE WHEN lr.overallRating IS NOT NULL THEN 1 ELSE 0 END), 0)) " +
            "FROM LocationReview lr WHERE lr.location.id = :locationId AND lr.isDeleted = false")
    Double getAverageRatingForLocation(@Param("locationId") Long locationId);

    // Broj utisaka
    @Query("SELECT COUNT(lr) FROM LocationReview lr WHERE lr.location.id = :locationId AND lr.isDeleted = false")
    Long getTotalReviewsForLocation(@Param("locationId") Long locationId);

    // Svi utisci jednog korisnika
    @Query("SELECT lr FROM LocationReview lr WHERE lr.user.id = :userId AND lr.isDeleted = false")
    List<LocationReview> findByUserId(@Param("userId") Long userId);

    // M4 — utisci za lokaciju u periodu
    @Query("SELECT lr FROM LocationReview lr WHERE lr.location.id = :locationId AND lr.createdAt >= :start AND lr.createdAt <= :end AND lr.isDeleted = false")
    List<LocationReview> findByLocationIdAndPeriod(@Param("locationId") Long locationId,
                                                    @Param("start") java.time.LocalDateTime start,
                                                    @Param("end") java.time.LocalDateTime end);
}