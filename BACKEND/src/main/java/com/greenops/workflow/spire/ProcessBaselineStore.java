package com.greenops.workflow.spire;

import java.util.Set;

/** Thread-safe process baseline abstraction; production deployments can replace this with DynamoDB. */
public interface ProcessBaselineStore {
    Set<String> authorizedBinaries(String userId);

    long historicalExecutions(String userId, String binaryName);

    void recordAuthorizedExecution(String userId, String binaryName);

    default boolean isConfigured() { return false; }
}
