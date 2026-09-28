package com.sacolao.lead.controller;

import com.sacolao.auth.dto.MessageResponse;
import com.sacolao.lead.dto.CreateLeadRequest;
import com.sacolao.lead.service.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    public MessageResponse create(@Valid @RequestBody CreateLeadRequest request, HttpServletRequest httpRequest) {
        leadService.create(request, clientIp(httpRequest));
        return new MessageResponse("Recebemos. Vamos chamar você nesse WhatsApp.");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
