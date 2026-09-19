package com.sacolao.order.repository;

import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByPublicCode(String publicCode);

    @Query("""
            select o from Order o
            join fetch o.customer
            left join fetch o.items
            where o.id = :id and o.establishment.id = :establishmentId
            """)
    Optional<Order> findDetailedByIdAndEstablishmentId(
            @Param("id") UUID id,
            @Param("establishmentId") UUID establishmentId
    );

    @Query("""
            select o from Order o
            join fetch o.customer
            left join fetch o.items
            where o.publicCode = :publicCode and o.establishment.id = :establishmentId
            """)
    Optional<Order> findDetailedByPublicCodeAndEstablishmentId(
            @Param("publicCode") String publicCode,
            @Param("establishmentId") UUID establishmentId
    );

    @Query("""
            select distinct o from Order o
            join fetch o.customer
            left join fetch o.items
            where o.establishment.id = :establishmentId
              and (:status is null or o.status = :status)
            order by o.createdAt desc
            """)
    List<Order> findAllDetailedInTenant(
            @Param("establishmentId") UUID establishmentId,
            @Param("status") OrderStatus status
    );

    long countByEstablishment_IdAndStatus(UUID establishmentId, OrderStatus status);

    long countByEstablishment_Id(UUID establishmentId);
}
