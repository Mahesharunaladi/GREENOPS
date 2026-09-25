package com.vigidock.controller;

import com.vigidock.model.ScanRecord;
import com.vigidock.policy.GuardrailPolicy;
import com.vigidock.policy.IacArtifact;
import com.vigidock.remediation.GitHubPullRequestService;
import com.vigidock.remediation.GitHubPullRequestService.GitHubRepository;
import com.vigidock.remediation.GitHubPullRequestService.PullRequestResult;
import com.vigidock.remediation.RemediationPlan;
import com.vigidock.remediation.RemediationPlanner;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides dry-run, patch-download, and policy-gated pull-request remediation endpoints.
 */
@RestController
@RequestMapping("/api/remediations")
public final class RemediationController {

    private final RemediationPlanner remediationPlanner;
    private final GitHubPullRequestService gitHubPullRequestService;

    public RemediationController(
            RemediationPlanner remediationPlanner, GitHubPullRequestService gitHubPullRequestService) {
        this.remediationPlanner = Objects.requireNonNull(remediationPlanner, "remediationPlanner must not be null");
        this.gitHubPullRequestService = Objects.requireNonNull(
                gitHubPullRequestService, "gitHubPullRequestService must not be null");
    }

    /**
     * Returns EPSS prioritization, policy results, and a side-by-side dry-run diff.
     */
    @PostMapping(value = "/dry-run", produces = MediaType.APPLICATION_JSON_VALUE)
    public RemediationPlan dryRun(@RequestBody RemediationRequest request) {
        return plan(request);
    }

    /**
     * Downloads the exact unified patch generated during a dry run.
     */
    @PostMapping(value = "/patch", produces = "text/x-diff")
    public ResponseEntity<byte[]> downloadPatch(@RequestBody RemediationRequest request) {
        RemediationPlan plan = plan(request);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vigidock-fix.patch");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.valueOf("text/x-diff"))
                .body(plan.fixProposal().unifiedDiff().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a GitHub pull request only after the corresponding guardrails have passed.
     */
    @PostMapping(value = "/pull-request", produces = MediaType.APPLICATION_JSON_VALUE)
    public PullRequestResult createPullRequest(@RequestBody PullRequestRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return gitHubPullRequestService.createPullRequest(request.repository(), plan(request.remediation()));
    }

    private RemediationPlan plan(RemediationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return remediationPlanner.plan(request.scanRecord(), request.artifact(), request.policies());
    }

    /**
     * Input for policy-aware dry-run and patch generation.
     */
    public record RemediationRequest(
            ScanRecord scanRecord, IacArtifact artifact, List<GuardrailPolicy> policies) {

        public RemediationRequest {
            Objects.requireNonNull(scanRecord, "scanRecord must not be null");
            Objects.requireNonNull(artifact, "artifact must not be null");
            policies = policies == null ? List.of() : List.copyOf(policies);
        }
    }

    /**
     * Input for the explicit GitHub write operation.
     */
    public record PullRequestRequest(RemediationRequest remediation, GitHubRepository repository) {
        public PullRequestRequest {
            Objects.requireNonNull(remediation, "remediation must not be null");
            Objects.requireNonNull(repository, "repository must not be null");
        }
    }
}
