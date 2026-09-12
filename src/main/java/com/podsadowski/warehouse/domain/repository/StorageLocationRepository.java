package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    Optional<StorageLocation> findByCode(String code);

    boolean existsByCode(String code);
}
