package com.sacolao.customer.service;

import com.sacolao.customer.dto.CustomerResponse;
import com.sacolao.customer.entity.Customer;
import com.sacolao.customer.mapper.CustomerMapper;
import com.sacolao.customer.repository.CustomerRepository;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return customerRepository.findByEstablishment_IdOrderByNameAsc(TenantContext.require()).stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }

    @Transactional
    public Customer findOrCreate(
            Establishment establishment,
            String name,
            String phone,
            String email
    ) {
        String normalizedPhone = normalizePhone(phone);
        return customerRepository.findByEstablishment_IdAndPhone(establishment.getId(), normalizedPhone)
                .map(existing -> updateProfile(existing, name, email))
                .orElseGet(() -> create(establishment, name, normalizedPhone, email));
    }

    private Customer create(Establishment establishment, String name, String phone, String email) {
        Customer customer = new Customer();
        customer.setEstablishment(establishment);
        customer.setName(name.trim());
        customer.setPhone(phone);
        customer.setEmail(blankToNull(email));
        return customerRepository.save(customer);
    }

    private Customer updateProfile(Customer customer, String name, String email) {
        if (name != null && !name.isBlank()) {
            customer.setName(name.trim());
        }
        if (email != null) {
            customer.setEmail(blankToNull(email));
        }
        return customer;
    }

    public static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("\\D+", "");
        return digits.isBlank() ? phone.trim() : digits;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
