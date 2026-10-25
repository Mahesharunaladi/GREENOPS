package com.greenops.innovation.swarm;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Scores pre-aggregated CloudTrail/VPC activity; collection and account scoping belong to the AWS adapter. */
@Component
public final class CloudTrailAgent {
    public RiskAssessment assess(CloudActivity activity) {
        double spike = activity.baselineGetObjectCount() == 0
                ? (activity.getObjectCount() > 0 ? 1 : 0)
                : Math.min(1, (double) activity.getObjectCount() / Math.max(1, activity.baselineGetObjectCount()) / 5d);
        double score = Math.min(1, spike * .7 + (activity.unusualApiCall() ? .3 : 0));
        return new RiskAssessment("cloudtrail", score, .35,
                Map.of("getObjectCount", Long.toString(activity.getObjectCount()), "unusualApiCall", Boolean.toString(activity.unusualApiCall())));
    }
    public record CloudActivity(long getObjectCount, long baselineGetObjectCount, boolean unusualApiCall) {
        public CloudActivity { if (getObjectCount < 0 || baselineGetObjectCount < 0) throw new IllegalArgumentException("counts must be non-negative"); }
    }
}
