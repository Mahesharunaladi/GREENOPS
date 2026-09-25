package com.vigidock.remediation;

import com.vigidock.epss.EpssRiskService;
import com.vigidock.epss.PrioritizedScan;
import com.vigidock.model.ScanRecord;
import com.vigidock.policy.GuardrailEvaluation;
import com.vigidock.policy.GuardrailPolicy;
import com.vigidock.policy.GuardrailPolicyEngine;
import com.vigidock.policy.IacArtifact;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Coordinates EPSS enrichment, guardrail evaluation, and an in-memory IaC fix proposal.
 */
@Service
public final class RemediationPlanner {

    private final EpssRiskService epssRiskService;
    private final GuardrailPolicyEngine guardrailPolicyEngine;
    private final IacFixGenerator iacFixGenerator;

    public RemediationPlanner(
            EpssRiskService epssRiskService,
            GuardrailPolicyEngine guardrailPolicyEngine,
            IacFixGenerator iacFixGenerator) {
        this.epssRiskService = Objects.requireNonNull(epssRiskService, "epssRiskService must not be null");
        this.guardrailPolicyEngine = Objects.requireNonNull(guardrailPolicyEngine, "guardrailPolicyEngine must not be null");
        this.iacFixGenerator = Objects.requireNonNull(iacFixGenerator, "iacFixGenerator must not be null");
    }

    /**
     * Builds a fully reviewable remediation plan without modifying source control.
     */
    public RemediationPlan plan(ScanRecord scanRecord, IacArtifact artifact, List<GuardrailPolicy> policies) {
        PrioritizedScan prioritizedScan = epssRiskService.enrich(scanRecord);
        GuardrailEvaluation evaluation = guardrailPolicyEngine.evaluate(artifact, scanRecord, policies);
        FixProposal proposal = iacFixGenerator.generate(artifact, scanRecord, evaluation);
        return new RemediationPlan(prioritizedScan, evaluation, proposal, Instant.now());
    }
}
