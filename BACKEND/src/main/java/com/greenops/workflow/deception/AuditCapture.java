package com.greenops.workflow.deception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Allowlist-only header snapshot and bounded payload redaction for honeypot audit records. */
final class AuditCapture {
    private static final int MAX_CAPTURE_CHARS = 8192;
    private AuditCapture() {}

    static String capture(Map<String, ? extends List<String>> headers, String payload) {
        Map<String, String> safe = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String key = name.toLowerCase(java.util.Locale.ROOT);
            if (!values.isEmpty()) {
                String captured = sensitiveHeader(key) ? "[REDACTED]" : redact(String.join(",", values));
                safe.put(key, captured.length() <= 512 ? captured : captured.substring(0, 512));
            }
        });
        String body = redact(payload == null ? "" : payload);
        String serialized = "headers=" + safe + "; payload=" + body;
        return serialized.length() <= MAX_CAPTURE_CHARS ? serialized : serialized.substring(0, MAX_CAPTURE_CHARS);
    }

    private static String redact(String value) {
        String cleaned = value.replaceAll("(?i)(\\\"?(?:password|passwd|secret|token|api[_-]?key|authorization|cookie|session)\\\"?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|[^&,\\s}]+)", "$1[REDACTED]");
        cleaned = cleaned.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "Bearer [REDACTED]");
        cleaned = cleaned.replaceAll("[\\r\\n\\u0000]", " ");
        return cleaned.length() <= MAX_CAPTURE_CHARS ? cleaned : cleaned.substring(0, MAX_CAPTURE_CHARS);
    }

    private static boolean sensitiveHeader(String name) {
        return name.contains("authorization") || name.contains("cookie") || name.contains("token")
                || name.contains("secret") || name.contains("password") || name.contains("api-key")
                || name.contains("api_key") || name.contains("credential");
    }
}
