package com.sacolao.stock.repository;

import com.sacolao.stock.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findTop20ByProduct_IdAndEstablishment_IdOrderByCreatedAtDesc(UUID productId, UUID establishmentId);
}
