package com.vigidock.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.vigidock.model.ScanRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailPolicyEngineTest {

    private final GuardrailPolicyEngine policyEngine = new GuardrailPolicyEngine();

    @Test
    void blocksDockerfileThatRunsAsRootAndContainsCriticalFindings() {
        IacArtifact artifact = new IacArtifact(
                IacArtifact.ArtifactType.DOCKERFILE,
                "Dockerfile",
                "FROM eclipse-temurin:17\nUSER root\n",
                null);
        ScanRecord scanRecord = new ScanRecord(
                "service:latest",
                Instant.parse("2026-09-25T00:00:00Z"),
                1,
                0,
                0,
                0,
                List.of(new ScanRecord.VulnerabilityFinding("CVE-2026-0001", "openssl", "Explanation", "Upgrade openssl", 9.8d)));

        GuardrailEvaluation evaluation = policyEngine.evaluate(
                artifact,
                scanRecord,
                List.of(
                        new GuardrailPolicy("no-root", GuardrailPolicy.RuleType.DISALLOW_ROOT_USER, true, ""),
                        new GuardrailPolicy("severity-gate", GuardrailPolicy.RuleType.BLOCK_MINIMUM_SEVERITY, true, "CRITICAL")));

        assertThat(evaluation.allowed()).isFalse();
        assertThat(evaluation.violations()).hasSize(2);
    }
}
