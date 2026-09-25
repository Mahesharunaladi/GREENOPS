package com.vigidock.policy;

import java.util.Map;
import java.util.Objects;

/**
 * In-memory Dockerfile or Kubernetes manifest evaluated by VigiDock guardrails.
 */
public record IacArtifact(ArtifactType type, String path, String content, Map<String, String> metadata) {

    private static final int MAX_CONTENT_LENGTH = 1_000_000;

    public IacArtifact {
        Objects.requireNonNull(type, "type must not be null");
        path = requireText(path, "path");
        if (path.startsWith("/") || path.contains("\\") || path.equals("..") || path.startsWith("../") || path.contains("/../")) {
            throw new IllegalArgumentException("path must be a repository-relative file path");
        }
        content = requireText(content, "content");
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content exceeds the 1 MB dry-run limit");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public enum ArtifactType {
        DOCKERFILE,
        KUBERNETES
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.toString(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
