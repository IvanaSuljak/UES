package com.example.newnow.service;

import com.example.newnow.model.Location;
import com.example.newnow.model.User;
import java.util.List;
import java.util.Optional;

public interface LocationService {
    List<Location> findAll();
    List<Location> findAllValid();
    Optional<Location> findById(Long id);
    Location save(Location location);
    void deleteById(Long id);

    List<Location> searchByName(String name);
    List<Location> filterByType(String type);
    List<Location> searchByNameAndType(String name, String type);
    List<Location> searchByAddress(String address);

    // K6 — unified search
    List<Location> searchLocations(String name, String address, String type);

    Location findByManager(User manager);
}