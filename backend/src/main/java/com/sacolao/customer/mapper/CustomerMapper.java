package com.sacolao.customer.mapper;

import com.sacolao.customer.dto.CustomerResponse;
import com.sacolao.customer.entity.Customer;

public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getEstablishmentId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getCreatedAt()
        );
    }
}
