package com.sacolao.coupon.repository;

import com.sacolao.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findByEstablishment_IdOrderByCreatedAtDesc(UUID establishmentId);

    Optional<Coupon> findByIdAndEstablishment_Id(UUID id, UUID establishmentId);

    Optional<Coupon> findByEstablishment_IdAndCodeIgnoreCase(UUID establishmentId, String code);

    boolean existsByEstablishment_IdAndCodeIgnoreCase(UUID establishmentId, String code);
}
