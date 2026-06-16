package com.example.newnow.service_impl;

import com.example.newnow.model.Location;
import com.example.newnow.model.User;
import com.example.newnow.repository.LocationRepository;
import com.example.newnow.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;

    @Autowired
    public LocationServiceImpl(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public List<Location> findAll() {
        return locationRepository.findAll();
    }

    @Override
    public List<Location> findAllValid() {
        return locationRepository.findAllValidLocations();
    }

    @Override
    public Optional<Location> findById(Long id) {
        return locationRepository.findById(id);
    }

    @Override
    public Location save(Location location) {
        return locationRepository.save(location);
    }

    @Override
    public void deleteById(Long id) {
        locationRepository.deleteById(id);
    }

    @Override
    public List<Location> searchByName(String name) {
        return locationRepository.findByNameContainingIgnoreCase(name);
    }

    @Override
    public List<Location> filterByType(String type) {
        return locationRepository.findByType(type);
    }

    @Override
    public List<Location> searchByNameAndType(String name, String type) {
        return locationRepository.findByNameContainingIgnoreCaseAndType(name, type);
    }

    @Override
    public List<Location> searchByAddress(String address) {
        return locationRepository.findByAddressContainingIgnoreCase(address);
    }

    @Override
    public List<Location> searchLocations(String name, String address, String type) {
        return locationRepository.searchLocations(name, address, type);
    }

    @Override
    public Location findByManager(User manager) {
        return locationRepository.findByManager(manager).orElse(null);
    }
}