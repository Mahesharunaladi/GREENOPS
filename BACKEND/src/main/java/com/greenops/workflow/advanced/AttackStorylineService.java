package com.greenops.workflow.advanced;

import com.greenops.model.AttackStorylineEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

@Service
public final class AttackStorylineService {
    private static final Logger log = LoggerFactory.getLogger(AttackStorylineService.class);
    private final DynamoDbAsyncClient dynamo;
    private final String tableName;

    public AttackStorylineService(DynamoDbAsyncClient dynamo,
            @Value("${greenops.advanced.storyline-table:}") String tableName) {
        this.dynamo = dynamo; this.tableName = tableName;
    }

    public boolean isConfigured() { return !tableName.isBlank(); }

    public Mono<Void> recordStorylineEvent(String sessionId, String stage, String actionDetails, String sourceIp) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 256) return Mono.error(new IllegalArgumentException("sessionId is required"));
        if (actionDetails == null || actionDetails.isBlank() || actionDetails.length() > 4096) return Mono.error(new IllegalArgumentException("actionDetails is required and limited to 4 KiB"));
        if (sourceIp != null && sourceIp.length() > 64) return Mono.error(new IllegalArgumentException("sourceIp is too long"));
        if (tableName.isBlank()) return Mono.error(new IllegalStateException("GREENOPS_ADVANCED_STORYLINE_TABLE is not configured"));
        AttackStorylineEvent event;
        try { event = new AttackStorylineEvent(UUID.randomUUID().toString(), sessionId, stage, actionDetails, sourceIp == null ? "unknown" : sourceIp, Instant.now()); }
        catch (IllegalArgumentException error) { return Mono.error(error); }
        String eventKey = event.timestamp().toString() + "#" + event.eventId();
        Map<String, AttributeValue> item = Map.of(
                "sessionId", s(event.sessionId()), "eventKey", s(eventKey), "eventId", s(event.eventId()),
                "stage", s(event.stage()), "actionDetails", s(event.actionDetails()), "sourceIp", s(event.sourceIp()),
                "timestamp", s(event.timestamp().toString()));
        return Mono.fromFuture(dynamo.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())).then()
                .doOnSuccess(ignored -> log.info("Attack storyline event recorded: sessionId={}, stage={}, eventId={}", sessionId, stage, event.eventId()));
    }

    public Flux<AttackStorylineEvent> getSessionStoryline(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 256) return Flux.error(new IllegalArgumentException("sessionId is required"));
        if (tableName.isBlank()) return Flux.error(new IllegalStateException("GREENOPS_ADVANCED_STORYLINE_TABLE is not configured"));
        QueryRequest request = QueryRequest.builder().tableName(tableName).keyConditionExpression("#session = :session")
                .expressionAttributeNames(Map.of("#session", "sessionId"))
                .expressionAttributeValues(Map.of(":session", s(sessionId))).scanIndexForward(true).limit(500).build();
        return Mono.fromFuture(dynamo.query(request)).flatMapMany(response -> Flux.fromIterable(response.items()))
                .map(this::fromItem)
                .doOnError(error -> log.warn("Could not load attack storyline for sessionId={}, errorType={}", sessionId, error.getClass().getSimpleName()));
    }

    private AttackStorylineEvent fromItem(Map<String, AttributeValue> item) {
        return new AttackStorylineEvent(item.get("eventId").s(), item.get("sessionId").s(), item.get("stage").s(),
                item.get("actionDetails").s(), item.get("sourceIp").s(), Instant.parse(item.get("timestamp").s()));
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
}
