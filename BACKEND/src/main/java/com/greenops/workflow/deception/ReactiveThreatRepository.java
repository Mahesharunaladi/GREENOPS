package com.greenops.workflow.deception;

import com.greenops.model.AttackerActionLog;
import com.greenops.model.ThreatFingerprint;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Reactive persistence hook for a DynamoDB-backed implementation. */
public interface ReactiveThreatRepository {
    Mono<Optional<ThreatFingerprint>> findFingerprint(String compositeHash);
    Mono<Void> saveFingerprint(ThreatFingerprint fingerprint);
    Mono<Void> saveAction(AttackerActionLog actionLog);
}
