package com.sacolao.lead.repository;

import com.sacolao.lead.entity.CommercialLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommercialLeadRepository extends JpaRepository<CommercialLead, UUID> {

    List<CommercialLead> findAllByOrderByCreatedAtDesc();
}
