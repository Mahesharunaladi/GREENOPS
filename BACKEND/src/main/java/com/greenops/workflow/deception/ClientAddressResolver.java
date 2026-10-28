package com.greenops.workflow.deception;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Uses X-Forwarded-For only when the immediate peer is an explicitly trusted proxy. */
@Component
public final class ClientAddressResolver {
    private final Set<String> trustedProxies;

    public ClientAddressResolver(@Value("${greenops.deception.trusted-proxies:}") String configured) {
        trustedProxies = Arrays.stream(configured.split(",")).map(String::trim).filter(v -> !v.isBlank())
                .map(this::normalize).collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(String peerAddress, String forwardedFor) {
        String peer = normalize(peerAddress);
        if (trustedProxies.contains(peer) && forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            if (validAddress(first)) return normalize(first);
        }
        return validAddress(peer) ? peer : "unknown";
    }

    private boolean validAddress(String value) {
        return value != null && !value.isBlank() && value.length() <= 64 && value.matches("[0-9a-fA-F:.%]+") && !value.contains("..") && !value.contains("\n");
    }

    private String normalize(String value) {
        if (value == null) return "unknown";
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.contains("]")) normalized = normalized.substring(1, normalized.indexOf(']'));
        if (normalized.startsWith("::ffff:")) normalized = normalized.substring(7);
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }
}
