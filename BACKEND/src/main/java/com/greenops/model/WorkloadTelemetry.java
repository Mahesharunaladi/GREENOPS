package com.greenops.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable, bounded process event submitted for workload attestation. */
public record WorkloadTelemetry(
        @NotBlank @Size(max = 128) String sessionId,
        @NotBlank @Size(max = 128) String userId,
        @NotBlank @Size(max = 16_384) String spiffeSvid,
        @NotBlank @Size(max = 256) String binaryName,
        @NotBlank @Size(max = 2_048) String binaryPath,
        @Size(max = 64) List<@Size(max = 2_048) String> executedArguments,
        @Size(max = 64) List<@Size(max = 512) String> resourceTargets,
        @PositiveOrZero long processPid,
        @NotBlank @Size(max = 256) String hostNodeAttestationHash,
        Instant timestamp) {

    public WorkloadTelemetry {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(spiffeSvid, "spiffeSvid");
        Objects.requireNonNull(binaryName, "binaryName");
        Objects.requireNonNull(binaryPath, "binaryPath");
        Objects.requireNonNull(hostNodeAttestationHash, "hostNodeAttestationHash");
        executedArguments = executedArguments == null ? List.of() : List.copyOf(executedArguments);
        resourceTargets = resourceTargets == null ? List.of() : List.copyOf(resourceTargets);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
