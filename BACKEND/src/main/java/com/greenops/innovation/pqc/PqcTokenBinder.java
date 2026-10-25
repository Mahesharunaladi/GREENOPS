package com.greenops.innovation.pqc;

import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.util.Base64;
import java.util.Objects;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

/** Verifies compact JWS identity tokens signed with a configured ML-DSA key. */
@Component
public final class PqcTokenBinder {
    private static final String ML_DSA = "ML-DSA";

    public PqcTokenBinder() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Validates the JWS signature. Key selection and issuer/audience/expiry validation remain the
     * caller's responsibility; never select a key from an untrusted token header alone.
     */
    public boolean verifyMlDsaJws(String compactJws, PublicKey trustedPublicKey) {
        Objects.requireNonNull(trustedPublicKey, "trustedPublicKey must not be null");
        if (compactJws == null) return false;
        String[] parts = compactJws.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) return false;
        try {
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
            if (!header.matches("(?s).*\\\"alg\\\"\\s*:\\s*\\\"ML-DSA-65\\\".*")) return false;
            Signature verifier = Signature.getInstance(ML_DSA, BouncyCastleProvider.PROVIDER_NAME);
            verifier.initVerify(trustedPublicKey);
            verifier.update((parts[0] + "." + parts[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
        } catch (Exception invalid) {
            return false;
        }
    }
}
