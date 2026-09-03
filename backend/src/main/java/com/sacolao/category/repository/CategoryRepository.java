package com.sacolao.category.repository;

import com.sacolao.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByIdAndEstablishment_Id(UUID id, UUID establishmentId);

    List<Category> findByEstablishment_IdOrderBySortOrderAscNameAsc(UUID establishmentId);

    List<Category> findByEstablishment_IdAndActiveTrueOrderBySortOrderAscNameAsc(UUID establishmentId);

    boolean existsByEstablishment_IdAndNameIgnoreCase(UUID establishmentId, String name);

    boolean existsByEstablishment_IdAndNameIgnoreCaseAndIdNot(UUID establishmentId, String name, UUID id);
}
