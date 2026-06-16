package com.example.newnow.service_impl;

import com.example.newnow.model.Event;
import com.example.newnow.repository.EventRepository;
import com.example.newnow.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;

    @Autowired
    public EventServiceImpl(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public List<Event> findAll() {
        return eventRepository.findAll();
    }

    @Override
    public Optional<Event> findById(Long id) {
        return eventRepository.findById(id);
    }

    @Override
    public Event save(Event event) {
        return eventRepository.save(event);
    }

    @Override
    public void deleteById(Long id) {
        eventRepository.deleteById(id);
    }

    @Override
    public List<Event> findByLocationId(Long locationId) {
        return eventRepository.findByLocationId(locationId);
    }

    // 🟢 K6: Pretraga i filtriranje
    @Override
    public List<Event> searchByType(String type) {
        return eventRepository.findByTypeContainingIgnoreCase(type);
    }

    @Override
    public List<Event> searchByLocationName(String locationName) {
        return eventRepository.findByLocationName(locationName);
    }

    @Override
    public List<Event> searchByAddress(String address) {
        return eventRepository.findByAddress(address);
    }

    @Override
    public List<Event> filterByPriceRange(Double minPrice, Double maxPrice) {
        return eventRepository.findByPriceRange(minPrice, maxPrice);
    }

    @Override
    public List<Event> findFreeEvents() {
        return eventRepository.findFreeEvents();
    }

    @Override
    public List<Event> findTodayEvents(LocalDateTime startOfDay, LocalDateTime endOfDay) {
        return eventRepository.findTodayEvents(startOfDay, endOfDay);
    }

    @Override
    public Long countEventOccurrences(Long eventId) {
        return eventRepository.countPastOccurrences(eventId, LocalDateTime.now());
    }

    @Override
    public List<Event> findByDate(java.time.LocalDate date) {
        return eventRepository.findByDate(date);
    }
}