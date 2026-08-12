package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ScanResult;
import java.util.List;

public interface AuditLogRepository {
    void save(ScanResult result);
    List<ScanResult> findAll();
}
