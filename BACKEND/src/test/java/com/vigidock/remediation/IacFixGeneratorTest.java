package com.vigidock.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.vigidock.model.ScanRecord;
import com.vigidock.policy.GuardrailEvaluation;
import com.vigidock.policy.IacArtifact;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IacFixGeneratorTest {

    private final IacFixGenerator fixGenerator = new IacFixGenerator();

    @Test
    void producesReviewableDockerfilePatchForExplicitRootUser() {
        IacArtifact artifact = new IacArtifact(
                IacArtifact.ArtifactType.DOCKERFILE,
                "Dockerfile",
                "FROM eclipse-temurin:17\nUSER root\n",
                null);
        ScanRecord scanRecord = new ScanRecord(
                "service:latest",
                Instant.now(),
                0,
                1,
                0,
                0,
                List.of(new ScanRecord.VulnerabilityFinding("CVE-2026-0002", "curl", "Explanation", "Upgrade curl", 7.5d)));

        FixProposal proposal = fixGenerator.generate(
                artifact,
                scanRecord,
                new GuardrailEvaluation(true, List.of(), Instant.now()));

        assertThat(proposal.updatedContent()).contains("USER 10001");
        assertThat(proposal.unifiedDiff()).contains("--- a/Dockerfile");
        assertThat(proposal.sideBySideDiff())
                .anyMatch(line -> line.changeType() == FixProposal.ChangeType.MODIFIED);
    }
}
