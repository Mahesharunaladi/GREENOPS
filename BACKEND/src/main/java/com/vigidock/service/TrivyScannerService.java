package com.vigidock.service;

import com.vigidock.model.ScanResponse;
import com.vigidock.model.VulnerabilityDetail;
import com.vigidock.model.VulnerabilityDetail.RiskTag;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Enriches raw Trivy CVE findings with FIRST EPSS exploit likelihood data.
 *
 * <p>Trivy execution remains outside this service's responsibility. Callers supply raw findings from
 * their scanner adapter, and this service returns a UI-ready response ordered by real-world risk.
 */
@Service
public final class TrivyScannerService {

    private final EpssService epssService;

    public TrivyScannerService(EpssService epssService) {
        this.epssService = Objects.requireNonNull(epssService, "epssService must not be null");
    }

    /**
     * Enriches raw Trivy findings with EPSS and returns prioritized vulnerability tags.
     *
     * @param scan raw scan result supplied by the Trivy adapter
     * @return scan response with EPSS score, percentile, and prioritization tag per finding
     */
    public ScanResponse enrich(TrivyScanResult scan) {
        Objects.requireNonNull(scan, "scan must not be null");
        List<VulnerabilityDetail> vulnerabilities = scan.findings().stream()
                .map(this::enrichFinding)
                .sorted(Comparator
                        .comparingInt((VulnerabilityDetail detail) -> rank(detail.riskTag()))
                        .thenComparing(VulnerabilityDetail::epssScore, Comparator.reverseOrder())
                        .thenComparing(VulnerabilityDetail::cvssScore, Comparator.reverseOrder()))
                .toList();
        return new ScanResponse(
                scan.targetName(),
                scan.scannedAt(),
                count(vulnerabilities, 9.0d, Double.MAX_VALUE),
                count(vulnerabilities, 7.0d, 9.0d),
                count(vulnerabilities, 4.0d, 7.0d),
                count(vulnerabilities, 0.0d, 4.0d),
                vulnerabilities);
    }

    private VulnerabilityDetail enrichFinding(RawTrivyFinding finding) {
        EpssService.EpssResult epssResult = epssService.lookup(finding.cveId());
        RiskTag riskTag = VulnerabilityDetail.riskTagFor(finding.cvssScore(), epssResult.epss());
        return new VulnerabilityDetail(
                finding.cveId(),
                finding.packageName(),
                finding.cvssScore(),
                epssResult.epss(),
                epssResult.percentile(),
                riskTag,
                finding.aiExplanation(),
                finding.remediationFix());
    }

    private int count(List<VulnerabilityDetail> vulnerabilities, double inclusiveMinimum, double exclusiveMaximum) {
        return (int) vulnerabilities.stream()
                .filter(detail -> detail.cvssScore() >= inclusiveMinimum && detail.cvssScore() < exclusiveMaximum)
                .count();
    }

    private int rank(RiskTag riskTag) {
        return switch (riskTag) {
            case URGENT_ACTUAL_THREAT -> 0;
            case STANDARD_PRIORITY -> 1;
            case THEORETICAL_RISK_LOW_PRIORITY -> 2;
        };
    }

    /**
     * Scanner-agnostic raw Trivy scan output accepted for EPSS enrichment.
     */
    public record TrivyScanResult(String targetName, Instant scannedAt, List<RawTrivyFinding> findings) {
        public TrivyScanResult {
            if (targetName == null || targetName.isBlank()) {
                throw new IllegalArgumentException("targetName must not be blank");
            }
            targetName = targetName.trim();
            scannedAt = scannedAt == null ? Instant.now() : scannedAt;
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }

    /**
     * Raw scanner finding prior to EPSS enrichment.
     */
    public record RawTrivyFinding(
            String cveId, String packageName, double cvssScore, String aiExplanation, String remediationFix) {

        public RawTrivyFinding {
            if (cveId == null || cveId.isBlank() || packageName == null || packageName.isBlank()) {
                throw new IllegalArgumentException("cveId and packageName must not be blank");
            }
            if (!Double.isFinite(cvssScore) || cvssScore < 0.0d || cvssScore > 10.0d) {
                throw new IllegalArgumentException("cvssScore must be between 0 and 10");
            }
            if (aiExplanation == null || aiExplanation.isBlank() || remediationFix == null || remediationFix.isBlank()) {
                throw new IllegalArgumentException("aiExplanation and remediationFix must not be blank");
            }
        }
    }
}
