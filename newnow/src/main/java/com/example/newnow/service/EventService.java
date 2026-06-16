package com.example.newnow.service;

import com.example.newnow.model.Event;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventService {
    List<Event> findAll();
    Optional<Event> findById(Long id);
    Event save(Event event);
    void deleteById(Long id);
    List<Event> findByLocationId(Long locationId);

    // 🟢 K6: Pretraga i filtriranje
    List<Event> searchByType(String type);
    List<Event> searchByLocationName(String locationName);
    List<Event> searchByAddress(String address);
    List<Event> filterByPriceRange(Double minPrice, Double maxPrice);
    List<Event> findFreeEvents();
    List<Event> findTodayEvents(LocalDateTime startOfDay, LocalDateTime endOfDay);

    Long countEventOccurrences(Long eventId);

    // K6 — filter po proizvoljnom datumu
    List<Event> findByDate(java.time.LocalDate date);
}