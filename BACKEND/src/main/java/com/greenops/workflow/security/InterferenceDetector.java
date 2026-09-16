package com.greenops.workflow.security;

import com.greenops.workflow.security.SecurityThreatAssessment.ThreatLevel;
import com.greenops.workflow.telemetry.TelemetryEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class InterferenceDetector {

    private static final int DEFAULT_SPIKE_CALLS_PER_MINUTE = 180;
    private static final int DEFAULT_CRITICAL_CALLS_PER_MINUTE = 600;
    private static final Duration DEFAULT_DRIFT_WINDOW = Duration.ofMinutes(5);
    private static final Map<String, String> JA3_BROWSER_HINT_ATTRIBUTES = Map.of(
            "ja3.browserFamily", "explicit JA3 browser-family hint",
            "tls.client.browserFamily", "TLS client browser-family hint",
            "expectedBrowserFamily", "expected TLS browser-family hint");

    private final ActiveSessionRecordProvider activeSessionRecordProvider;

    /**
     * Creates a detector with an optional active session provider.
     *
     * <p>Spring supplies {@link ObjectProvider} even when no Redis/DynamoDB adapter bean exists. In
     * that case the detector still evaluates self-contained JA3 and API-frequency signals, while
     * emitting a low-severity reason that no active session baseline was available.
     *
     * @param activeSessionRecordProviderProvider provider for a DynamoDB/Redis-backed session adapter
     */
    public InterferenceDetector(ObjectProvider<ActiveSessionRecordProvider> activeSessionRecordProviderProvider) {
        this.activeSessionRecordProvider = activeSessionRecordProviderProvider.getIfAvailable(NoActiveSessionRecordProvider::new);
    }

    /**
     * Evaluates the telemetry event for MITM, token hijacking, and third-party extension/proxy
     * interference indicators.
     *
     * @param event normalized telemetry event from the ingestion workflow
     * @return immutable assessment containing the highest threat level and detection reasons
     */
    public SecurityThreatAssessment detectInterference(TelemetryEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        List<Finding> findings = new ArrayList<>();
        Optional<ActiveSessionRecord> activeSession =
                activeSessionRecordProvider.findBySessionTokenHash(event.sessionTokenHash());

        if (activeSession.isPresent()) {
            evaluateDeviceDrift(event, activeSession.get(), findings);
            evaluateApiFrequency(event, activeSession.get(), findings);
        } else {
            findings.add(new Finding(
                    ThreatLevel.LOW,
                    "No active session baseline found in DynamoDB/Redis for session token hash"));
            evaluateApiFrequencyWithoutSessionBaseline(event, findings);
        }

        evaluateJa3UserAgentMismatch(event, activeSession, findings);
        evaluateTokenContext(event, findings);
        evaluateAutomationHints(event, findings);

        ThreatLevel threatLevel = findings.stream()
                .map(finding -> Objects.requireNonNull(finding, "finding must not be null").threatLevel())
                .max(InterferenceDetector::compareThreatLevel)
                .orElse(ThreatLevel.LOW);
        List<String> reasons = findings.stream()
                .map(finding -> Objects.requireNonNull(finding, "finding must not be null").reason())
                .distinct()
                .toList();

        return new SecurityThreatAssessment(
                event.eventId(),
                event.sessionTokenHash(),
                threatLevel,
                reasons.isEmpty() ? List.of("No third-party interference indicators detected") : reasons,
                Instant.now());
    }

    private void evaluateDeviceDrift(
            TelemetryEvent event, ActiveSessionRecord activeSession, List<Finding> findings) {
        TelemetryEvent.DeviceContext current = event.deviceContext();

        if (!equalsIgnoreUnknown(activeSession.clientIp(), current.clientIp())) {
            ThreatLevel severity = "public".equals(current.ipScope()) ? ThreatLevel.HIGH : ThreatLevel.MEDIUM;
            findings.add(new Finding(
                    severity,
                    "Client IP drift detected against active session baseline"));
        }
        if (!equalsIgnoreUnknown(activeSession.tlsJa3Fingerprint(), current.tlsJa3Fingerprint())) {
            findings.add(new Finding(
                    ThreatLevel.HIGH,
                    "TLS JA3 fingerprint drift detected against active session baseline"));
        }
        if (!equalsIgnoreUnknown(activeSession.browserFamily(), current.browserFamily())) {
            findings.add(new Finding(
                    ThreatLevel.MEDIUM,
                    "Browser family drift detected against active session baseline"));
        }
        if (!equalsIgnoreUnknown(activeSession.operatingSystem(), current.operatingSystem())) {
            findings.add(new Finding(
                    ThreatLevel.MEDIUM,
                    "Operating system drift detected against active session baseline"));
        }

        if (activeSession.lastObservedAt() != null
                && event.observedAt().isAfter(activeSession.lastObservedAt())
                && Duration.between(activeSession.lastObservedAt(), event.observedAt()).compareTo(DEFAULT_DRIFT_WINDOW) <= 0
                && !equalsIgnoreUnknown(activeSession.clientIp(), current.clientIp())
                && !equalsIgnoreUnknown(activeSession.tlsJa3Fingerprint(), current.tlsJa3Fingerprint())) {
            findings.add(new Finding(
                    ThreatLevel.CRITICAL,
                    "Rapid simultaneous IP and TLS fingerprint drift indicates likely token hijacking or MITM"));
        }
    }

    private void evaluateJa3UserAgentMismatch(
            TelemetryEvent event, Optional<ActiveSessionRecord> activeSession, List<Finding> findings) {
        String claimedBrowser = normalize(event.deviceContext().browserFamily());
        String ja3BrowserHint = ja3BrowserHint(event.attributes())
                .or(() -> activeSession.map(session -> Objects.requireNonNull(
                        session, "active session must not be null").tlsJa3BrowserFamily()))
                .map(InterferenceDetector::normalize)
                .orElse("unknown");

        if (!"unknown".equals(ja3BrowserHint)
                && !"unknown".equals(claimedBrowser)
                && !ja3BrowserHint.equals(claimedBrowser)) {
            findings.add(new Finding(
                    ThreatLevel.HIGH,
                    "TLS JA3 fingerprint browser hint does not match claimed User-Agent browser family"));
        }

        if ("unknown".equals(event.deviceContext().tlsJa3Fingerprint())
                && !"unknown".equals(claimedBrowser)
                && !"curl".equals(claimedBrowser)) {
            findings.add(new Finding(
                    ThreatLevel.MEDIUM,
                    "Missing TLS JA3 fingerprint for interactive browser session"));
        }
    }

    private void evaluateApiFrequency(
            TelemetryEvent event, ActiveSessionRecord activeSession, List<Finding> findings) {
        int callsPerMinute = integerAttribute(event.attributes(), "api.callsPerMinute")
                .orElse(activeSession.apiCallsPerMinute());
        int spikeThreshold = positiveIntegerAttribute(event.attributes(), "api.spikeThresholdPerMinute")
                .orElse(DEFAULT_SPIKE_CALLS_PER_MINUTE);
        int criticalThreshold = positiveIntegerAttribute(event.attributes(), "api.criticalThresholdPerMinute")
                .orElse(DEFAULT_CRITICAL_CALLS_PER_MINUTE);

        if (callsPerMinute >= criticalThreshold) {
            findings.add(new Finding(
                    ThreatLevel.CRITICAL,
                    "Critical API call frequency spike indicates rogue proxy or bot automation"));
        } else if (callsPerMinute >= spikeThreshold) {
            findings.add(new Finding(
                    ThreatLevel.HIGH,
                    "Sudden API call frequency spike exceeds active session automation threshold"));
        }
    }

    private void evaluateApiFrequencyWithoutSessionBaseline(TelemetryEvent event, List<Finding> findings) {
        integerAttribute(event.attributes(), "api.callsPerMinute").ifPresent(callsPerMinute -> {
            if (callsPerMinute >= DEFAULT_CRITICAL_CALLS_PER_MINUTE) {
                findings.add(new Finding(
                        ThreatLevel.CRITICAL,
                        "Critical API call frequency spike detected without active session baseline"));
            } else if (callsPerMinute >= DEFAULT_SPIKE_CALLS_PER_MINUTE) {
                findings.add(new Finding(
                        ThreatLevel.HIGH,
                        "API call frequency spike detected without active session baseline"));
            }
        });
    }

    private void evaluateTokenContext(TelemetryEvent event, List<Finding> findings) {
        TelemetryEvent.TokenContext tokenContext = event.tokenContext();
        if (!tokenContext.jwtFormat()) {
            findings.add(new Finding(
                    ThreatLevel.MEDIUM,
                    "Session token is not a well-formed OAuth/OIDC JWT"));
        }
        if (tokenContext.expiresAt() != null && tokenContext.expiresAt().isBefore(Instant.now())) {
            findings.add(new Finding(
                    ThreatLevel.CRITICAL,
                    "Expired JWT observed in active telemetry stream"));
        }
    }

    private void evaluateAutomationHints(TelemetryEvent event, List<Finding> findings) {
        if (event.deviceContext().automationSuspected()) {
            findings.add(new Finding(
                    ThreatLevel.HIGH,
                    "User-Agent indicates bot, crawler, headless browser, or automation framework"));
        }
        if (event.mouseTelemetry().sampleCount() == 0 && event.keystrokeTelemetry().featureVector().isEmpty()) {
            findings.add(new Finding(
                    ThreatLevel.MEDIUM,
                    "Interactive session telemetry contains no mouse or keystroke behavior"));
        }
    }

    private static Optional<String> ja3BrowserHint(Map<String, String> attributes) {
        for (String key : JA3_BROWSER_HINT_ATTRIBUTES.keySet()) {
            String value = attributes.get(key);
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> integerAttribute(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> positiveIntegerAttribute(Map<String, String> attributes, String key) {
        return integerAttribute(attributes, key).filter(value -> value > 0);
    }

    private static boolean equalsIgnoreUnknown(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return "unknown".equals(normalizedLeft)
                || "unknown".equals(normalizedRight)
                || normalizedLeft.equals(normalizedRight);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int compareThreatLevel(ThreatLevel left, ThreatLevel right) {
        return Integer.compare(left.ordinal(), right.ordinal());
    }

    /**
     * Persistence port for active session state.
     *
     * <p>Provide a Redis or DynamoDB adapter bean that implements this interface to enable
     * historical drift checks. The lookup key is the SHA-256 token hash, never the raw bearer token.
     */
    public interface ActiveSessionRecordProvider {

        /**
         * Looks up the active session baseline by token hash.
         *
         * @param sessionTokenHash SHA-256 hash emitted by {@code TelemetryIngestor}
         * @return active session baseline when one exists
         */
        Optional<ActiveSessionRecord> findBySessionTokenHash(String sessionTokenHash);
    }

    /**
     * Active session baseline stored by a Redis or DynamoDB adapter.
     *
     * @param sessionTokenHash SHA-256 token hash
     * @param clientIp last trusted client IP
     * @param tlsJa3Fingerprint last trusted TLS JA3 fingerprint
     * @param browserFamily browser family derived from the trusted User-Agent
     * @param tlsJa3BrowserFamily optional browser-family classification from JA3 intelligence
     * @param operatingSystem operating system derived from the trusted User-Agent
     * @param apiCallsPerMinute recent request rate for this session
     * @param lastObservedAt last session observation timestamp
     */
    public record ActiveSessionRecord(
            String sessionTokenHash,
            String clientIp,
            String tlsJa3Fingerprint,
            String browserFamily,
            String tlsJa3BrowserFamily,
            String operatingSystem,
            int apiCallsPerMinute,
            Instant lastObservedAt) {

        public ActiveSessionRecord {
            Objects.requireNonNull(sessionTokenHash, "sessionTokenHash must not be null");
            if (apiCallsPerMinute < 0) {
                throw new IllegalArgumentException("apiCallsPerMinute must be non-negative");
            }
        }
    }

    private record Finding(ThreatLevel threatLevel, String reason) {}

    private static final class NoActiveSessionRecordProvider implements ActiveSessionRecordProvider {
        @Override
        public Optional<ActiveSessionRecord> findBySessionTokenHash(String sessionTokenHash) {
            return Optional.empty();
        }
    }
}
