package com.sacolao.establishment.repository;

import com.sacolao.establishment.entity.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EstablishmentRepository extends JpaRepository<Establishment, UUID> {

    boolean existsBySlug(String slug);

    Optional<Establishment> findBySlug(String slug);
}
