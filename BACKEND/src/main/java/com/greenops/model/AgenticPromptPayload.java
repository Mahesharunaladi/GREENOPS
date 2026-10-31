package com.greenops.model;

import java.util.List;

public record AgenticPromptPayload(
        String promptId,
        String agentId,
        String userPrompt,
        String targetToolCall,
        List<String> detectedInjectionSignatures,
        boolean containsIndirectInjection) {
    public AgenticPromptPayload {
        detectedInjectionSignatures = detectedInjectionSignatures == null
                ? List.of() : List.copyOf(detectedInjectionSignatures);
        if (userPrompt != null && userPrompt.length() > 16_384) throw new IllegalArgumentException("userPrompt exceeds 16 KiB");
        if (targetToolCall != null && targetToolCall.length() > 8_192) throw new IllegalArgumentException("targetToolCall exceeds 8 KiB");
        if (detectedInjectionSignatures.size() > 64) throw new IllegalArgumentException("Too many injection signatures");
    }
}
