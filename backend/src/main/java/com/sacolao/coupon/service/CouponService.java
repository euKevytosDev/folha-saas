package com.sacolao.coupon.service;

import com.sacolao.common.exception.ConflictException;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.coupon.dto.CouponResponse;
import com.sacolao.coupon.dto.CreateCouponRequest;
import com.sacolao.coupon.dto.UpdateCouponRequest;
import com.sacolao.coupon.entity.Coupon;
import com.sacolao.coupon.entity.DiscountType;
import com.sacolao.coupon.repository.CouponRepository;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final EstablishmentRepository establishmentRepository;

    public CouponService(CouponRepository couponRepository, EstablishmentRepository establishmentRepository) {
        this.couponRepository = couponRepository;
        this.establishmentRepository = establishmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> list() {
        return couponRepository.findByEstablishment_IdOrderByCreatedAtDesc(TenantContext.require()).stream()
                .map(CouponService::toResponse)
                .toList();
    }

    @Transactional
    public CouponResponse create(CreateCouponRequest request) {
        Establishment establishment = requireTenantEstablishment();
        String code = normalizeCode(request.code());
        if (couponRepository.existsByEstablishment_IdAndCodeIgnoreCase(establishment.getId(), code)) {
            throw new ConflictException("COUPON_CODE_EXISTS", "Já existe um cupom com este código");
        }
        validateDiscount(request.discountType(), request.discountValue());
        Coupon coupon = new Coupon();
        coupon.setEstablishment(establishment);
        coupon.setCode(code);
        coupon.setDescription(blankToNull(request.description()));
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(Money.of(request.discountValue()));
        coupon.setMinOrderAmount(request.minOrderAmount() == null ? null : Money.of(request.minOrderAmount()));
        coupon.setMaxDiscountAmount(request.maxDiscountAmount() == null ? null : Money.of(request.maxDiscountAmount()));
        coupon.setUsageLimit(request.usageLimit());
        coupon.setActive(request.active() == null || request.active());
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        return toResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse update(UUID id, UpdateCouponRequest request) {
        Coupon coupon = requireInTenant(id);
        if (request.description() != null) {
            coupon.setDescription(blankToNull(request.description()));
        }
        if (request.discountType() != null) {
            coupon.setDiscountType(request.discountType());
        }
        if (request.discountValue() != null) {
            coupon.setDiscountValue(Money.of(request.discountValue()));
        }
        validateDiscount(coupon.getDiscountType(), coupon.getDiscountValue());
        if (request.minOrderAmount() != null) {
            coupon.setMinOrderAmount(Money.of(request.minOrderAmount()));
        }
        if (request.maxDiscountAmount() != null) {
            coupon.setMaxDiscountAmount(Money.of(request.maxDiscountAmount()));
        }
        if (request.usageLimit() != null) {
            coupon.setUsageLimit(request.usageLimit());
        }
        if (request.active() != null) {
            coupon.setActive(request.active());
        }
        if (request.startsAt() != null) {
            coupon.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            coupon.setEndsAt(request.endsAt());
        }
        return toResponse(coupon);
    }

    @Transactional
    public void delete(UUID id) {
        couponRepository.delete(requireInTenant(id));
    }

    @Transactional(readOnly = true)
    public AppliedCoupon apply(UUID establishmentId, String rawCode, BigDecimal subtotal) {
        if (rawCode == null || rawCode.isBlank()) {
            return AppliedCoupon.none();
        }
        String code = normalizeCode(rawCode);
        Coupon coupon = couponRepository.findByEstablishment_IdAndCodeIgnoreCase(establishmentId, code)
                .orElseThrow(() -> new UnprocessableException("COUPON_INVALID", "Cupom inválido"));
        Instant now = Instant.now();
        if (!coupon.isActive()) {
            throw new UnprocessableException("COUPON_INACTIVE", "Cupom inativo");
        }
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new UnprocessableException("COUPON_NOT_STARTED", "Cupom ainda não está válido");
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw new UnprocessableException("COUPON_EXPIRED", "Cupom expirado");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new UnprocessableException("COUPON_EXHAUSTED", "Cupom esgotado");
        }
        if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new UnprocessableException(
                    "COUPON_MIN_ORDER",
                    "Pedido mínimo de R$ " + coupon.getMinOrderAmount().toPlainString() + " para este cupom"
            );
        }
        BigDecimal discount = calculateDiscount(coupon, subtotal);
        return new AppliedCoupon(coupon, discount);
    }

    @Transactional
    public void markUsed(Coupon coupon) {
        coupon.setUsedCount(coupon.getUsedCount() + 1);
    }

    @Transactional
    public void releaseUsed(Coupon coupon) {
        if (coupon.getUsedCount() > 0) {
            coupon.setUsedCount(coupon.getUsedCount() - 1);
        }
    }

    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            discount = subtotal.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = Money.of(coupon.getMaxDiscountAmount());
            }
        } else {
            discount = Money.of(coupon.getDiscountValue());
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateDiscount(DiscountType type, BigDecimal value) {
        if (type == DiscountType.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new UnprocessableException("COUPON_PERCENT_INVALID", "Percentual máximo é 100");
        }
    }

    private Coupon requireInTenant(UUID id) {
        return couponRepository.findByIdAndEstablishment_Id(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }

    private Establishment requireTenantEstablishment() {
        return establishmentRepository.findById(TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }

    public static CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxDiscountAmount(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                coupon.isActive(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getCreatedAt()
        );
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AppliedCoupon(Coupon coupon, BigDecimal discount) {
        public static AppliedCoupon none() {
            return new AppliedCoupon(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        public boolean present() {
            return coupon != null;
        }
    }
}
