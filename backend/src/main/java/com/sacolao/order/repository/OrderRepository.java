package com.sacolao.order.repository;

import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByPublicCode(String publicCode);

    @Query("""
            select o from Order o
            join fetch o.customer
            left join fetch o.items i
            left join fetch i.product
            where o.id = :id and o.establishment.id = :establishmentId
            """)
    Optional<Order> findDetailedByIdAndEstablishmentId(
            @Param("id") UUID id,
            @Param("establishmentId") UUID establishmentId
    );

    @Query("""
            select o from Order o
            join fetch o.customer
            left join fetch o.items i
            left join fetch i.product
            where o.publicCode = :publicCode and o.establishment.id = :establishmentId
            """)
    Optional<Order> findDetailedByPublicCodeAndEstablishmentId(
            @Param("publicCode") String publicCode,
            @Param("establishmentId") UUID establishmentId
    );

    @Query("""
            select distinct o from Order o
            join fetch o.customer
            left join fetch o.items i
            left join fetch i.product
            where o.establishment.id = :establishmentId
              and o.historyHidden = false
              and (:status is null or o.status = :status)
            order by o.createdAt desc
            """)
    List<Order> findAllDetailedInTenant(
            @Param("establishmentId") UUID establishmentId,
            @Param("status") OrderStatus status
    );

    long countByEstablishment_IdAndStatusAndHistoryHiddenFalse(UUID establishmentId, OrderStatus status);

    long countByEstablishment_IdAndHistoryHiddenFalse(UUID establishmentId);

    @Query("""
            select o from Order o
            where o.establishment.id = :establishmentId
              and o.createdAt >= :start
              and o.createdAt < :end
            """)
    List<Order> findCreatedInPeriod(
            @Param("establishmentId") UUID establishmentId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            select o from Order o
            where o.establishment.id = :establishmentId
              and o.status = com.sacolao.order.entity.OrderStatus.CANCELLED
              and o.updatedAt >= :start
              and o.updatedAt < :end
            """)
    List<Order> findCancelledInPeriod(
            @Param("establishmentId") UUID establishmentId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
