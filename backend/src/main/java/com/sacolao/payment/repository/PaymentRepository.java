package com.sacolao.payment.repository;

import com.sacolao.payment.entity.Payment;
import com.sacolao.payment.entity.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrder_IdAndEstablishment_Id(UUID orderId, UUID establishmentId);

    Optional<Payment> findByProviderAndExternalId(PaymentProviderType provider, String externalId);

    Optional<Payment> findByExternalId(String externalId);

    @Query("""
            select p from Payment p
            join fetch p.order o
            where o.publicCode = :publicCode and p.establishment.id = :establishmentId
            """)
    Optional<Payment> findByOrderPublicCodeAndEstablishmentId(
            @Param("publicCode") String publicCode,
            @Param("establishmentId") UUID establishmentId
    );

    Optional<Payment> findByIdAndEstablishment_Id(UUID id, UUID establishmentId);

    @Query("""
            select p from Payment p
            where p.establishment.id = :establishmentId
              and p.status = com.sacolao.payment.entity.PaymentStatus.PAID
              and coalesce(p.paidAt, p.updatedAt) >= :start
              and coalesce(p.paidAt, p.updatedAt) < :end
            """)
    List<Payment> findPaidInPeriod(
            @Param("establishmentId") UUID establishmentId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            select p from Payment p
            where p.establishment.id = :establishmentId
              and p.order.id in :orderIds
            """)
    List<Payment> findByOrderIds(
            @Param("establishmentId") UUID establishmentId,
            @Param("orderIds") Collection<UUID> orderIds
    );
}
