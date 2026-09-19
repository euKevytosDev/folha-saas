package com.sacolao.customer.repository;

import com.sacolao.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEstablishment_IdAndPhone(UUID establishmentId, String phone);

    Optional<Customer> findByIdAndEstablishment_Id(UUID id, UUID establishmentId);

    List<Customer> findByEstablishment_IdOrderByNameAsc(UUID establishmentId);
}
