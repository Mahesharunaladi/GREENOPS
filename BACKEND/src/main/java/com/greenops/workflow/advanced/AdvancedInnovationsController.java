package com.greenops.workflow.advanced;

import com.greenops.model.AgenticPromptPayload;
import com.greenops.model.AttackStorylineEvent;
import com.greenops.model.PreAttackSnapshot;
import com.greenops.model.TtcRiskAssessment;
import com.greenops.workflow.deception.UserSessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Validated
@RequestMapping("/api/v1/advanced")
public class AdvancedInnovationsController {
    private final AgenticAiGuardService aiGuard;
    private final SelfHealingInfrastructureService selfHealing;
    private final PredictiveRiskEngine predictiveRisk;
    private final PredictiveRiskSignalProvider riskSignals;
    private final AttackStorylineService storyline;
    private final byte[] adminApiKey;

    public AdvancedInnovationsController(AgenticAiGuardService aiGuard,
            SelfHealingInfrastructureService selfHealing, PredictiveRiskEngine predictiveRisk,
            PredictiveRiskSignalProvider riskSignals, AttackStorylineService storyline,
            @Value("${greenops.enterprise.admin-api-key:}") String adminApiKey) {
        this.aiGuard = aiGuard; this.selfHealing = selfHealing; this.predictiveRisk = predictiveRisk;
        this.riskSignals = riskSignals; this.storyline = storyline; this.adminApiKey = adminApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/ai-guard/inspect")
    public Mono<Boolean> inspectPrompt(@Valid @RequestBody AgenticPromptPayload payload,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        return aiGuard.inspectAgentPrompt(payload);
    }

    @PostMapping("/infrastructure/snapshot")
    public Mono<PreAttackSnapshot> captureSnapshot(@Valid @RequestBody ResourceRequest request,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        return selfHealing.captureResourceSnapshot(request.resourceArn());
    }

    @PostMapping("/infrastructure/rollback")
    public Mono<RollbackResponse> rollback(@Valid @RequestBody RollbackRequest request,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        return selfHealing.rollbackInfrastructure(request.resourceArn(), request.snapshotId())
                .thenReturn(new RollbackResponse(request.snapshotId(), selfHealing.isDryRun() ? "DRY_RUN" : "ROLLED_BACK"));
    }

    @GetMapping("/risk/predictive-ttc/{userId}")
    public Mono<TtcRiskAssessment> predictiveTtc(@PathVariable String userId, HttpServletRequest request,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        if (!riskSignals.isConfigured()) return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Trusted network drift, device attestation, and biometric velocity adapter is not configured"));
        Object attribute = request.getAttribute(UserSessionContext.REQUEST_ATTRIBUTE);
        if (!(attribute instanceof UserSessionContext session)) return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Trusted current-session context is not available for this request"));
        return predictiveRisk.calculatePredictiveTtc(userId, session);
    }

    @GetMapping("/storyline/replay/{sessionId}")
    public Flux<AttackStorylineEvent> storyline(@PathVariable String sessionId,
            @RequestHeader(name = "X-GreenOps-Admin-Key", required = false) String key) {
        requireAdmin(key);
        return storyline.getSessionStoryline(sessionId);
    }

    private void requireAdmin(String supplied) {
        if (adminApiKey.length < 32 || supplied == null
                || !MessageDigest.isEqual(adminApiKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enterprise administrator access is required");
        }
    }

    public record ResourceRequest(@NotBlank String resourceArn) {}
    public record RollbackRequest(@NotBlank String resourceArn, @NotBlank String snapshotId) {}
    public record RollbackResponse(String snapshotId, String status) {}
}
