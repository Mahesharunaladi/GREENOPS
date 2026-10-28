package com.greenops.workflow.deception;

/** Trusted server-side signals; never construct this from request headers or body fields. */
public record UserSessionContext(boolean spireVerified, double biometricScore, String spiffeSvid, boolean blacklisted) {
    public static final String REQUEST_ATTRIBUTE = UserSessionContext.class.getName();
    public UserSessionContext {
        if (!Double.isFinite(biometricScore) || biometricScore < 0 || biometricScore > 1) {
            throw new IllegalArgumentException("biometricScore must be between 0 and 1");
        }
        spiffeSvid = spiffeSvid == null ? "unknown" : spiffeSvid;
    }
}
