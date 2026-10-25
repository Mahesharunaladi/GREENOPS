package com.greenops.innovation.cost;

import java.util.Objects;
import org.springframework.stereotype.Component;

/** Chooses inference depth from server-side session and endpoint context. */
@Component
public final class SecurityCostOptimizer {
    public Tier select(Context context) {
        Objects.requireNonNull(context);
        if (context.sensitiveEndpoint() || context.newLocation() || context.untrustedDevice() || context.riskScore() >= .60) return Tier.FULL;
        return Tier.RULE_BASED;
    }
    public enum Tier { RULE_BASED, FULL }
    public record Context(double riskScore, boolean newLocation, boolean untrustedDevice, boolean sensitiveEndpoint) {
        public Context { if (!Double.isFinite(riskScore) || riskScore < 0 || riskScore > 1) throw new IllegalArgumentException("riskScore must be in [0,1]"); }
    }
}
