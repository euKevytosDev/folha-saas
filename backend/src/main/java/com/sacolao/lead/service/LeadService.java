package com.sacolao.lead.service;

import com.sacolao.common.exception.TooManyRequestsException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.lead.dto.CreateLeadRequest;
import com.sacolao.lead.dto.LeadResponse;
import com.sacolao.lead.entity.CommercialLead;
import com.sacolao.lead.repository.CommercialLeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LeadService {

    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_SECONDS = 15 * 60;

    private final CommercialLeadRepository repository;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    public LeadService(CommercialLeadRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void create(CreateLeadRequest request, String clientIp) {
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }
        checkRate(clientIp);
        String phone = normalizePhone(request.phone());
        CommercialLead lead = new CommercialLead();
        lead.setName(request.name().trim());
        lead.setPhone(phone);
        lead.setBusinessName(request.businessName().trim());
        lead.setBrief(request.brief().trim());
        repository.save(lead);
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(lead -> new LeadResponse(
                        lead.getId(),
                        lead.getName(),
                        lead.getPhone(),
                        lead.getBusinessName(),
                        lead.getBrief(),
                        lead.getCreatedAt()
                ))
                .toList();
    }

    private void checkRate(String clientIp) {
        long now = Instant.now().getEpochSecond();
        ArrayDeque<Long> window = hits.computeIfAbsent(clientIp, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_SECONDS) {
                window.removeFirst();
            }
            if (window.size() >= MAX_PER_WINDOW) {
                throw new TooManyRequestsException("Muitas mensagens seguidas. Espere um pouco e tente de novo.");
            }
            window.addLast(now);
        }
    }

    private static String normalizePhone(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() > 11) {
            digits = digits.substring(2);
        }
        if (digits.length() < 10 || digits.length() > 11) {
            throw new UnprocessableException("PHONE_INVALID", "Informe um WhatsApp com DDD");
        }
        return digits;
    }
}
