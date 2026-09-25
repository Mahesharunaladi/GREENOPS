package com.vigidock.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.vigidock.model.ScanRecord;
import com.vigidock.policy.GuardrailEvaluation;
import com.vigidock.policy.IacArtifact;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KubernetesFixGeneratorTest {

    @Test
    void addsSecurityContextOnlyToContainerEntries() {
        String manifest = """
                apiVersion: v1
                kind: Pod
                spec:
                  containers:
                    - name: application
                      image: example/app:1.0
                      env:
                        - name: LOG_LEVEL
                          value: info
                """;
        IacArtifact artifact = new IacArtifact(IacArtifact.ArtifactType.KUBERNETES, "deployment.yaml", manifest, null);
        ScanRecord scanRecord = new ScanRecord("deployment.yaml", Instant.now(), 0, 0, 0, 0, List.of());

        FixProposal proposal = new IacFixGenerator().generate(
                artifact, scanRecord, new GuardrailEvaluation(true, List.of(), Instant.now()));

        assertThat(proposal.updatedContent()).contains("- name: application\n      securityContext:");
        assertThat(proposal.updatedContent()).doesNotContain("- name: LOG_LEVEL\n          securityContext:");
    }
}
