package com.sacolao.product.repository;

import com.sacolao.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndEstablishment_Id(UUID id, UUID establishmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Product p
            where p.id = :id and p.establishment.id = :establishmentId
            """)
    Optional<Product> findByIdAndEstablishmentIdForUpdate(
            @Param("id") UUID id,
            @Param("establishmentId") UUID establishmentId
    );

    @Query("""
            select p from Product p
            join fetch p.category
            where p.establishment.id = :establishmentId
            order by p.name
            """)
    List<Product> findAllInTenant(@Param("establishmentId") UUID establishmentId);

    boolean existsByCategory_IdAndEstablishment_Id(UUID categoryId, UUID establishmentId);

    @Query("""
            select p from Product p
            join fetch p.category c
            where p.establishment.id = :establishmentId
              and p.available = true
              and c.active = true
              and (:categoryId is null or c.id = :categoryId)
              and (:hasQuery = false or lower(p.name) like :query)
            order by p.featured desc, p.name
            """)
    List<Product> searchPublic(
            @Param("establishmentId") UUID establishmentId,
            @Param("categoryId") UUID categoryId,
            @Param("query") String query,
            @Param("hasQuery") boolean hasQuery
    );

    @Query("""
            select p from Product p
            join fetch p.category c
            where p.id = :id
              and p.establishment.id = :establishmentId
              and p.available = true
              and c.active = true
            """)
    Optional<Product> findPublicById(
            @Param("id") UUID id,
            @Param("establishmentId") UUID establishmentId
    );
}
