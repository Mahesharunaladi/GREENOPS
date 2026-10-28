package com.greenops.workflow.spire;

import com.greenops.workflow.remediation.IamSessionRevoker.IamPrincipal;
import java.util.Optional;

/** Resolves an authenticated application identity to an IAM principal using server-owned mappings. */
public interface IamPrincipalResolver {
    Optional<IamPrincipal> resolve(String sessionId, String userId);

    default boolean isConfigured() { return false; }
}
