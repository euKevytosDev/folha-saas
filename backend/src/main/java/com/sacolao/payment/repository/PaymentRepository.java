package com.sacolao.payment.repository;

import com.sacolao.payment.entity.Payment;
import com.sacolao.payment.entity.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
