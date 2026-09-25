package com.vigidock.remediation;

import com.vigidock.policy.IacArtifact;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, reviewable IaC change proposal. It does not mutate a working tree.
 */
public record FixProposal(
        IacArtifact artifact,
        String updatedContent,
        String unifiedDiff,
        List<DiffLine> sideBySideDiff,
        List<String> explanations,
        Instant generatedAt) {

    public FixProposal {
        Objects.requireNonNull(artifact, "artifact must not be null");
        Objects.requireNonNull(updatedContent, "updatedContent must not be null");
        Objects.requireNonNull(unifiedDiff, "unifiedDiff must not be null");
        sideBySideDiff = sideBySideDiff == null ? List.of() : List.copyOf(sideBySideDiff);
        explanations = explanations == null ? List.of() : List.copyOf(explanations);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }

    /**
     * Aligned source and proposed lines for UI side-by-side rendering.
     */
    public record DiffLine(int originalLine, String original, int updatedLine, String updated, ChangeType changeType) {
        public DiffLine {
            Objects.requireNonNull(original, "original must not be null");
            Objects.requireNonNull(updated, "updated must not be null");
            Objects.requireNonNull(changeType, "changeType must not be null");
        }
    }

    public enum ChangeType {
        UNCHANGED,
        MODIFIED,
        ADDED,
        REMOVED
    }
}
