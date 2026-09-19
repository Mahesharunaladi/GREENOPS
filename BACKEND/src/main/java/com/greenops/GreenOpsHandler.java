package com.greenops;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenops.scrapper.GreenOpsScrapperApplication;
import com.greenops.scrapper.model.ScanResult;
import com.greenops.scrapper.service.SlackNotifier;
import com.greenops.workflow.cost.ResourceScanner;
import com.greenops.workflow.identity.RiskEvaluationResult;
import com.greenops.workflow.identity.ZeroTrustAgent;
import com.greenops.workflow.remediation.ActionExecutor;
import com.greenops.workflow.remediation.ActionExecutor.ActionExecutionResult;
import com.greenops.workflow.remediation.IamSessionRevoker;
import com.greenops.workflow.remediation.IamSessionRevoker.IamPrincipal;
import com.greenops.workflow.remediation.IamSessionRevoker.PrincipalType;
import com.greenops.workflow.remediation.IamSessionRevoker.RevocationResult;
import com.greenops.workflow.risk.RiskScoreCalculator;
import com.greenops.workflow.risk.RiskScoreCalculator.CompositeRiskScore;
import com.greenops.workflow.security.InterferenceDetector;
import com.greenops.workflow.security.SecurityThreatAssessment;
import com.greenops.workflow.telemetry.TelemetryEvent;
import com.greenops.workflow.telemetry.TelemetryIngestor;
import com.greenops.workflow.telemetry.TelemetryIngestor.TelemetryPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Central AWS Lambda entry point for the GreenOps cost and Zero Trust workflows.
 *
 * <p>The handler accepts either JSON telemetry payloads or scheduled EventBridge invocations. For
 * telemetry events, it executes ingestion, concurrently runs cost scanning and interference
 * detection, evaluates continuous identity risk, calculates a composite score, and delegates
 * remediation/notification. For scheduled EventBridge executions, it runs the cost scan path only.
 *
 * <p><strong>Lambda performance:</strong> Spring context and the small worker pool are static so warm
 * invocations reuse initialized beans and threads.
 */
public final class GreenOpsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(GreenOpsHandler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ExecutorService WORKFLOW_EXECUTOR = Executors.newFixedThreadPool(2, new DaemonThreadFactory());
    private static volatile ConfigurableApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        Map<String, Object> event = normalizeInput(input);
        ConfigurableApplicationContext spring = applicationContext();
        boolean dryRun = Boolean.parseBoolean(String.valueOf(event.getOrDefault("dryRun", "true")));

        try {
            if (isScheduledEvent(event)) {
                return success(runScheduledScan(spring, dryRun));
            }
            return success(runTelemetryWorkflow(spring, event, dryRun));
        } catch (RuntimeException ex) {
            log.error("GreenOps workflow failed: {}", ex.getMessage(), ex);
            return Map.of(
                    "statusCode", 500,
                    "body", writeJson(Map.of(
                            "status", "failed",
                            "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
        }
    }

    private Map<String, Object> runTelemetryWorkflow(
            ConfigurableApplicationContext spring, Map<String, Object> event, boolean dryRun) {
        TelemetryIngestor telemetryIngestor = spring.getBean(TelemetryIngestor.class);
        ResourceScanner resourceScanner = spring.getBean(ResourceScanner.class);
        InterferenceDetector interferenceDetector = spring.getBean(InterferenceDetector.class);
        ZeroTrustAgent zeroTrustAgent = spring.getBean(ZeroTrustAgent.class);
        RiskScoreCalculator riskScoreCalculator = spring.getBean(RiskScoreCalculator.class);
        ActionExecutor actionExecutor = spring.getBean(ActionExecutor.class);
        IamSessionRevoker iamSessionRevoker = spring.getBean(IamSessionRevoker.class);
        SlackNotifier slackNotifier = spring.getBean(SlackNotifier.class);

        TelemetryEvent telemetryEvent = telemetryIngestor.ingest(toTelemetryPayload(event));

        CompletableFuture<ScanResult> scanFuture =
                CompletableFuture.supplyAsync(() -> resourceScanner.scan(dryRun), WORKFLOW_EXECUTOR);
        CompletableFuture<SecurityThreatAssessment> interferenceFuture = CompletableFuture.supplyAsync(
                () -> interferenceDetector.detectInterference(telemetryEvent), WORKFLOW_EXECUTOR);

        ScanResult scanResult = join(scanFuture, "resource scan");
        SecurityThreatAssessment securityAssessment = join(interferenceFuture, "interference detection");
        RiskEvaluationResult identityEvaluation = zeroTrustAgent.evaluateIdentityRisk(telemetryEvent);
        CompositeRiskScore compositeRiskScore =
                riskScoreCalculator.calculateRiskScore(scanResult, securityAssessment, identityEvaluation);

        Optional<ActionExecutionResult> actionExecutionResult = Optional.empty();
        if (scanResult.idleResources() > 0) {
            actionExecutionResult = Optional.of(actionExecutor.executeIdlePolicyActions(scanResult));
        }

        Optional<RevocationResult> revocationResult = Optional.empty();
        Optional<IamPrincipal> principal = iamPrincipal(event, telemetryEvent);
        if (shouldRevoke(securityAssessment, identityEvaluation, compositeRiskScore)) {
            if (principal.isPresent()) {
                revocationResult = Optional.of(iamSessionRevoker.revokeOnCriticalThreat(
                        principal.get(), securityAssessment, identityEvaluation, compositeRiskScore));
            } else {
                slackNotifier.postWorkflowNotification("GreenOps critical threat detected but no IAM principal was supplied for revocation. "
                        + "eventId=" + telemetryEvent.eventId()
                        + ", riskScore=" + compositeRiskScore.score());
            }
        }
        if (scanResult.idleResources() > 0 || shouldRevoke(securityAssessment, identityEvaluation, compositeRiskScore)) {
            slackNotifier.postWorkflowNotification(workflowSummary(
                    telemetryEvent, scanResult, securityAssessment, identityEvaluation, compositeRiskScore, revocationResult));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "completed");
        response.put("mode", "telemetry");
        response.put("eventId", telemetryEvent.eventId());
        response.put("scanResult", scanResult);
        response.put("securityAssessment", securityAssessment);
        response.put("identityEvaluation", identityEvaluation);
        response.put("compositeRiskScore", compositeRiskScore);
        actionExecutionResult.ifPresent(result -> response.put("actionExecutionResult", result));
        revocationResult.ifPresent(result -> response.put("revocationResult", result));
        return response;
    }

    private Map<String, Object> runScheduledScan(ConfigurableApplicationContext spring, boolean dryRun) {
        ScanResult scanResult = spring.getBean(ResourceScanner.class).scan(dryRun);
        ActionExecutionResult actionExecutionResult =
                spring.getBean(ActionExecutor.class).executeIdlePolicyActions(scanResult);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "completed");
        response.put("mode", "scheduled");
        response.put("scanResult", scanResult);
        response.put("actionExecutionResult", actionExecutionResult);
        return response;
    }

    private TelemetryPayload toTelemetryPayload(Map<String, Object> event) {
        Object telemetry = event.getOrDefault("telemetry", event);
        return objectMapper.convertValue(telemetry, TelemetryPayload.class);
    }

    @SuppressWarnings("unchecked")
    private Optional<IamPrincipal> iamPrincipal(Map<String, Object> event, TelemetryEvent telemetryEvent) {
        Object rawPrincipal = event.get("iamPrincipal");
        if (rawPrincipal instanceof Map<?, ?> principalMap) {
            return toIamPrincipal((Map<String, Object>) principalMap);
        }
        String type = telemetryEvent.attributes().get("iam.principalType");
        String name = telemetryEvent.attributes().get("iam.principalName");
        if (type == null || name == null || type.isBlank() || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IamPrincipal(PrincipalType.valueOf(type.trim().toUpperCase()), name));
    }

    private Optional<IamPrincipal> toIamPrincipal(Map<String, Object> principalMap) {
        Object type = principalMap.get("type");
        Object name = principalMap.get("name");
        if (type == null || name == null) {
            return Optional.empty();
        }
        return Optional.of(new IamPrincipal(
                PrincipalType.valueOf(String.valueOf(type).trim().toUpperCase()),
                String.valueOf(name)));
    }

    private boolean shouldRevoke(
            SecurityThreatAssessment securityAssessment,
            RiskEvaluationResult identityEvaluation,
            CompositeRiskScore compositeRiskScore) {
        return "invoke_iam_session_revoker".equals(compositeRiskScore.recommendedAction())
                || "revoke_session".equals(identityEvaluation.action())
                || securityAssessment.threatLevel() == SecurityThreatAssessment.ThreatLevel.CRITICAL;
    }

    private String workflowSummary(
            TelemetryEvent event,
            ScanResult scanResult,
            SecurityThreatAssessment securityAssessment,
            RiskEvaluationResult identityEvaluation,
            CompositeRiskScore compositeRiskScore,
            Optional<RevocationResult> revocationResult) {
        return "GreenOps workflow completed"
                + "\nEvent: " + event.eventId()
                + "\nRisk: " + String.format(java.util.Locale.ROOT, "%.2f", compositeRiskScore.score())
                + " (" + compositeRiskScore.riskBand() + ")"
                + "\nSecurity: " + securityAssessment.threatLevel()
                + "\nIdentity action: " + identityEvaluation.action()
                + "\nIdle resources: " + scanResult.idleResources()
                + "\nRecommended action: " + compositeRiskScore.recommendedAction()
                + revocationResult.map(result -> "\nIAM revocation: " + (result.successful() ? "succeeded" : "failed/skipped"))
                        .orElse("");
    }

    private <T> T join(CompletableFuture<T> future, String stageName) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalStateException("Failed during " + stageName + ": " + cause.getMessage(), cause);
        }
    }

    private Map<String, Object> normalizeInput(Map<String, Object> input) {
        if (input == null) {
            return Map.of();
        }
        Object body = input.get("body");
        if (body instanceof String bodyText && !bodyText.isBlank()) {
            try {
                return objectMapper.readValue(bodyText, MAP_TYPE);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("Lambda body must be valid JSON", ex);
            }
        }
        return input;
    }

    private boolean isScheduledEvent(Map<String, Object> event) {
        String source = String.valueOf(event.getOrDefault("source", ""));
        String detailType = String.valueOf(event.getOrDefault("detail-type", ""));
        return "aws.events".equals(source)
                || "Scheduled Event".equalsIgnoreCase(detailType)
                || (!event.containsKey("telemetry") && !event.containsKey("sessionToken"));
    }

    private Map<String, Object> success(Map<String, Object> body) {
        return Map.of("statusCode", 200, "body", writeJson(body));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize GreenOps workflow response", ex);
        }
    }

    private ConfigurableApplicationContext applicationContext() {
        if (applicationContext == null) {
            synchronized (GreenOpsHandler.class) {
                if (applicationContext == null) {
                    applicationContext = new SpringApplicationBuilder(GreenOpsScrapperApplication.class)
                            .properties(Map.of("spring.main.web-application-type", "none"))
                            .run();
                }
            }
        }
        return applicationContext;
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "greenops-workflow-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
