package com.vigidock.controller;

import com.vigidock.model.ScanRecord;
import com.vigidock.service.PdfReportService;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes downloadable VigiDock security analysis reports.
 */
@RestController
@RequestMapping("/api/reports")
public final class ReportController {

    private final PdfReportService pdfReportService;

    public ReportController(PdfReportService pdfReportService) {
        this.pdfReportService = Objects.requireNonNull(pdfReportService, "pdfReportService must not be null");
    }

    /**
     * Builds and downloads a PDF report for the supplied security scan.
     *
     * @param scanRecord report-safe scan data
     * @return PDF resource with attachment download headers
     */
    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> downloadPdfReport(@RequestBody ScanRecord scanRecord) {
        ByteArrayOutputStream outputStream = pdfReportService.generateReport(scanRecord);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vigidock-report.pdf");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(outputStream.size())
                .body(new ByteArrayResource(outputStream.toByteArray()));
    }
}
