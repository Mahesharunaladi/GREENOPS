package com.greenops.scrapper.web;

import com.greenops.scrapper.model.ScanResult;
import com.greenops.scrapper.service.AuditLogRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public List<ScanResult> all() {
        return auditLogRepository.findAll();
    }
}
