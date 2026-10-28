package com.greenops.workflow.deception;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/** No external writes are made until a real repository adapter is supplied. */
@Configuration
public class DeceptionRepositoryDefaults {
    @Bean
    @ConditionalOnMissingBean(ReactiveThreatRepository.class)
    ReactiveThreatRepository inMemoryOnlyThreatRepository() {
        return new ReactiveThreatRepository() {
            public Mono<Optional<com.greenops.model.ThreatFingerprint>> findFingerprint(String hash) { return Mono.just(Optional.empty()); }
            public Mono<Void> saveFingerprint(com.greenops.model.ThreatFingerprint fingerprint) { return Mono.empty(); }
            public Mono<Void> saveAction(com.greenops.model.AttackerActionLog actionLog) { return Mono.empty(); }
        };
    }
}
