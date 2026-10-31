package com.greenops.model;

import java.time.Instant;

public record PreAttackSnapshot(String snapshotId, String resourceArn, String originalConfigJson, Instant capturedAt) {}
