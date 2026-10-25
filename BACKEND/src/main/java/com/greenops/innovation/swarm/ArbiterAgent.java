package com.greenops.innovation.swarm;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class ArbiterAgent {
    private final double threshold;
    public ArbiterAgent() { this(.80); }
    public ArbiterAgent(double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) throw new IllegalArgumentException("threshold must be in [0,1]");
        this.threshold = threshold;
    }
    public Consensus decide(List<RiskAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) throw new IllegalArgumentException("assessments must not be empty");
        double weights = 0;
        for (RiskAssessment assessment : assessments) {
            weights += assessment.weight();
        }
        if (weights == 0) throw new IllegalArgumentException("at least one agent weight must be positive");
        double score = assessments.stream().mapToDouble(a -> a.score() * a.weight()).sum() / weights;
        long severeVotes = assessments.stream().filter(a -> a.score() >= .90).count();
        boolean revoke = score >= threshold && severeVotes >= 2;
        return new Consensus(score, revoke, assessments.stream().toList(), revoke ? "revoke_session" : score >= .60 ? "step_up" : "allow");
    }
    public record Consensus(double score, boolean revokeIamSession, List<RiskAssessment> assessments, String action) {
        public Consensus { assessments = List.copyOf(assessments); }
    }
}
