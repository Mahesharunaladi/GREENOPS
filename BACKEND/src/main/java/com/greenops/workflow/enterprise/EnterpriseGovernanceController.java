package com.greenops.workflow.enterprise;

import com.greenops.model.BlastRadiusMetrics;
import com.greenops.model.CanaryToken;
import com.greenops.model.ComplianceEvaluationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Validated
@RequestMapping("/api/v1/enterprise")
public final class EnterpriseGovernanceController {
    private final ComplianceEvaluator compliance;
    private final BlastRadiusEngine blastRadius;
    private final CaepEventTransmitter caep;
    private final CanaryTokenService canaries;
    private final String defaultAccountId;
    private final byte[] adminApiKey;

    public EnterpriseGovernanceController(ComplianceEvaluator compliance, BlastRadiusEngine blastRadius,
            CaepEventTransmitter caep, CanaryTokenService canaries,
            @Value("${greenops.enterprise.aws-account-id:}") String defaultAccountId,
            @Value("${greenops.enterprise.admin-api-key:}") String adminApiKey) {
        this.compliance = compliance; this.blastRadius = blastRadius; this.caep = caep; this.canaries = canaries; this.defaultAccountId = defaultAccountId;
        this.adminApiKey = adminApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/compliance/score")
    public Mono<ComplianceEvaluationResult> complianceScore(@RequestParam(required = false) String awsAccountId,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        String account = awsAccountId == null || awsAccountId.isBlank() ? defaultAccountId : awsAccountId;
        return compliance.evaluateCompliancePosture(account);
    }

    @GetMapping("/blast-radius/{userId}")
    public Mono<BlastRadiusMetrics> blastRadius(@PathVariable String userId, @RequestParam String iamRoleArn,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        return blastRadius.calculateBlastRadius(userId, iamRoleArn);
    }

    @PostMapping("/caep/revoke-session")
    public Mono<ResponseEntity<Void>> revokeSession(@Valid @RequestBody CaepRevokeRequest request,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        return caep.broadcastSecurityEventToken(request.userId(), request.reason(), request.riskLevel())
                .thenReturn(ResponseEntity.accepted().<Void>build());
    }

    @PostMapping("/canary/tokens")
    public Mono<CanaryToken> generateCanary(@Valid @RequestBody CanaryRequest request,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        return Mono.fromSupplier(() -> canaries.generateHoneyCredentials(request.sessionId()));
    }

    /** Public capability URL embedded in synthetic records. Token IDs are random, opaque, and expire with the bounded cache. */
    @org.springframework.web.bind.annotation.RequestMapping(value = "/canary/trigger/{tokenId}",
            method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    public Mono<ResponseEntity<Void>> canaryTrigger(@PathVariable String tokenId, HttpServletRequest request) {
        return canaries.handleCanaryTrigger(tokenId, request.getRemoteAddr())
                .thenReturn(ResponseEntity.accepted().<Void>build());
    }

    private void requireAdmin(String supplied) {
        if (adminApiKey.length < 32 || supplied == null
                || !MessageDigest.isEqual(adminApiKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enterprise administrator access is required");
        }
    }

    public record CaepRevokeRequest(
            @NotBlank String userId,
            @NotBlank String reason,
            @NotBlank @Pattern(regexp = "(?i)LOW|MEDIUM|HIGH|CRITICAL") String riskLevel) {}

    public record CanaryRequest(@NotBlank String sessionId) {}
}
