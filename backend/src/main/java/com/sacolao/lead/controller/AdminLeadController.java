package com.sacolao.lead.controller;

import com.sacolao.lead.dto.LeadResponse;
import com.sacolao.lead.service.LeadService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/leads")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminLeadController {

    private final LeadService leadService;

    public AdminLeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public List<LeadResponse> list() {
        return leadService.list();
    }
}
