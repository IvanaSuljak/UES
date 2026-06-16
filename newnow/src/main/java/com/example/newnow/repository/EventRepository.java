package com.example.newnow.repository;

import com.example.newnow.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByLocationId(Long locationId);

    // K6 — filter po proizvoljnom datumu
    @Query("SELECT e FROM Event e WHERE CAST(e.dateTime AS date) = :date ORDER BY e.dateTime ASC")
    List<Event> findByDate(@Param("date") java.time.LocalDate date);

    List<Event> findByTypeContainingIgnoreCase(String type);

    @Query("SELECT e FROM Event e WHERE LOWER(e.location.name) LIKE LOWER(CONCAT('%', :locationName, '%'))")
    List<Event> findByLocationName(@Param("locationName") String locationName);

    @Query("SELECT e FROM Event e WHERE LOWER(e.location.address) LIKE LOWER(CONCAT('%', :address, '%'))")
    List<Event> findByAddress(@Param("address") String address);

    @Query("SELECT e FROM Event e WHERE (e.price IS NULL OR (e.price >= :minPrice AND e.price <= :maxPrice))")
    List<Event> findByPriceRange(@Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice);

    @Query("SELECT e FROM Event e WHERE e.price IS NULL")
    List<Event> findFreeEvents();

    @Query("SELECT e FROM Event e WHERE e.dateTime >= :startOfDay AND e.dateTime < :endOfDay ORDER BY e.dateTime ASC")
    List<Event> findTodayEvents(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    // Brojim koliko puta se dogadjaj desio pre nekog datuma
    @Query("SELECT COUNT(e) FROM Event e WHERE e.id = :eventId AND e.dateTime < :currentDateTime")
    Long countPastOccurrences(@Param("eventId") Long eventId, @Param("currentDateTime") LocalDateTime currentDateTime);
}