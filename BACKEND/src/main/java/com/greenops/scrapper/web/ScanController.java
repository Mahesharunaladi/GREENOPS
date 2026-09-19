package com.greenops.scrapper.web;

import com.greenops.scrapper.model.ScanResult;
import com.greenops.scrapper.service.ScanOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scan")
public class ScanController {

    private final ScanOrchestrator scanOrchestrator;

    public ScanController(ScanOrchestrator scanOrchestrator) {
        this.scanOrchestrator = scanOrchestrator;
    }

    @GetMapping
    public ResponseEntity<ScanResult> scan(@RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(scanOrchestrator.runScan(dryRun));
    }

    @PostMapping
    public ResponseEntity<ScanResult> scanPost(@RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(scanOrchestrator.runScan(dryRun));
    }
}
