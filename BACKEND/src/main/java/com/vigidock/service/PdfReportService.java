package com.vigidock.service;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.vigidock.model.ScanRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Produces downloadable VigiDock AI security analysis reports using iText 7.
 *
 * <p>Each invocation creates an independent PDF document in a caller-owned byte stream. The service
 * holds no mutable state and is safe to use concurrently from Spring MVC request threads.
 */
@Service
public final class PdfReportService {

    private static final Color BRAND_BLUE = new DeviceRgb(20, 67, 116);
    private static final Color LABEL_BACKGROUND = new DeviceRgb(237, 242, 247);
    private static final Color CRITICAL = new DeviceRgb(190, 24, 93);
    private static final Color HIGH = new DeviceRgb(220, 38, 38);
    private static final Color MEDIUM = new DeviceRgb(217, 119, 6);
    private static final Color LOW = new DeviceRgb(22, 163, 74);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    /**
     * Generates a complete VigiDock PDF report in memory.
     *
     * @param scanRecord security scan statistics, findings, and AI remediation guidance
     * @return populated PDF byte stream ready for a {@code ByteArrayResource}
     */
    public ByteArrayOutputStream generateReport(ScanRecord scanRecord) {
        Objects.requireNonNull(scanRecord, "scanRecord must not be null");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (PdfWriter writer = new PdfWriter(outputStream);
                PdfDocument pdfDocument = new PdfDocument(writer);
                Document document = new Document(pdfDocument)) {
            document.setMargins(36.0f, 36.0f, 42.0f, 36.0f);
            addHeader(document);
            addExecutiveSummary(document, scanRecord);
            addFindings(document, scanRecord);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate VigiDock PDF report", ex);
        }
        return outputStream;
    }

    private void addHeader(Document document) {
        document.add(new Paragraph("VigiDock AI - Security Analysis Report")
                .setFontSize(20.0f)
                .setBold()
                .setFontColor(BRAND_BLUE)
                .setMarginBottom(4.0f));
        document.add(new Paragraph("Automated vulnerability analysis with AI-guided remediation")
                .setFontSize(10.0f)
                .setFontColor(ColorConstants.DARK_GRAY)
                .setMarginBottom(18.0f));
    }

    private void addExecutiveSummary(Document document, ScanRecord scanRecord) {
        document.add(sectionTitle("Executive Summary"));
        Table summary = new Table(UnitValue.createPercentArray(new float[] {2.4f, 1.6f, 1.0f, 1.0f, 1.0f, 1.0f}))
                .useAllAvailableWidth()
                .setMarginBottom(18.0f);

        addHeaderCell(summary, "Image / YAML Name");
        addHeaderCell(summary, "Scan Date");
        addHeaderCell(summary, "Critical");
        addHeaderCell(summary, "High");
        addHeaderCell(summary, "Medium");
        addHeaderCell(summary, "Low");

        summary.addCell(valueCell(scanRecord.imageOrYamlName()));
        summary.addCell(valueCell(DATE_FORMATTER.format(scanRecord.scannedAt())));
        summary.addCell(badgeCell(scanRecord.criticalCount(), CRITICAL));
        summary.addCell(badgeCell(scanRecord.highCount(), HIGH));
        summary.addCell(badgeCell(scanRecord.mediumCount(), MEDIUM));
        summary.addCell(badgeCell(scanRecord.lowCount(), LOW));
        document.add(summary);
    }

    private void addFindings(Document document, ScanRecord scanRecord) {
        document.add(sectionTitle("Detailed Findings"));
        if (scanRecord.findings().isEmpty()) {
            document.add(new Paragraph("No vulnerability findings were reported for this scan.")
                    .setFontSize(10.0f)
                    .setFontColor(ColorConstants.DARK_GRAY));
            return;
        }

        for (ScanRecord.VulnerabilityFinding finding : scanRecord.findings()) {
            Table findingTable = new Table(UnitValue.createPercentArray(new float[] {1.35f, 4.65f}))
                    .useAllAvailableWidth()
                    .setMarginBottom(14.0f)
                    .setBorder(new SolidBorder(new DeviceRgb(203, 213, 225), 0.75f));
            addFindingRow(findingTable, "CVE ID", finding.cveId(), true);
            addFindingRow(findingTable, "Package", finding.packageName(), false);
            addFindingRow(findingTable, "AI Explanation", finding.aiExplanation(), false);
            addFindingRow(findingTable, "Remediation Fix", finding.remediationFix(), false);
            document.add(findingTable);
        }
    }

    private Paragraph sectionTitle(String title) {
        return new Paragraph(title)
                .setFontSize(14.0f)
                .setBold()
                .setFontColor(BRAND_BLUE)
                .setMarginTop(6.0f)
                .setMarginBottom(8.0f);
    }

    private void addHeaderCell(Table table, String value) {
        table.addHeaderCell(new Cell()
                .setBackgroundColor(BRAND_BLUE)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(7.0f)
                .add(new Paragraph(value).setBold().setFontSize(9.0f)));
    }

    private Cell valueCell(String value) {
        return new Cell()
                .setPadding(7.0f)
                .setFontSize(9.0f)
                .add(new Paragraph(value));
    }

    private Cell badgeCell(int value, Color color) {
        return new Cell()
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(7.0f)
                .add(new Paragraph(Integer.toString(value))
                        .setBold()
                        .setFontColor(color)
                        .setFontSize(10.0f));
    }

    private void addFindingRow(Table table, String label, String value, boolean highlighted) {
        table.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(LABEL_BACKGROUND)
                .setPadding(7.0f)
                .add(new Paragraph(label).setBold().setFontSize(9.0f)));
        Paragraph paragraph = new Paragraph(value).setFontSize(9.5f).setMultipliedLeading(1.2f);
        if (highlighted) {
            paragraph.setFontColor(HIGH).setBold();
        }
        table.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(7.0f).add(paragraph));
    }
}
