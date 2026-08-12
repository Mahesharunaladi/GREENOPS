package com.greenops.scrapper.service;

import com.greenops.scrapper.model.ScanResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAuditLogRepository implements AuditLogRepository {

    private final CopyOnWriteArrayList<ScanResult> entries = new CopyOnWriteArrayList<>();

    @Override
    public void save(ScanResult result) {
        entries.add(result);
    }

    @Override
    public List<ScanResult> findAll() {
        return List.copyOf(entries);
    }
}
