package com.example.newnow.repository;

import com.example.newnow.model.Location;
import com.example.newnow.model.User;  // 🟢 DODAJ
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("SELECT l FROM Location l WHERE l.name IS NOT NULL AND l.name <> ''")
    List<Location> findAllValidLocations();

    List<Location> findByNameContainingIgnoreCase(String name);
    List<Location> findByType(String type);
    List<Location> findByNameContainingIgnoreCaseAndType(String name, String type);
    List<Location> findByAddressContainingIgnoreCase(String address);

    Optional<Location> findByManager(User manager);

    long countByManagerIdAndIdNot(Long managerId, Long id);

    // K6 — unified search: name, address, type (null = bez tog filtera)
    @Query("SELECT l FROM Location l WHERE " +
           "(:name IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:address IS NULL OR LOWER(l.address) LIKE LOWER(CONCAT('%', :address, '%'))) AND " +
           "(:type IS NULL OR LOWER(l.type) LIKE LOWER(CONCAT('%', :type, '%')))")
    List<Location> searchLocations(
            @Param("name") String name,
            @Param("address") String address,
            @Param("type") String type
    );
}