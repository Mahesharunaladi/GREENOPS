package com.greenops.innovation.hardware;

import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Validates an attestation chain against operator-managed roots and delegates format-specific checks. */
public final class EnclaveAttestationValidator {
    private final AtomicReference<TrustConfiguration> trust;
    private final AttestationStatementVerifier statementVerifier;

    public EnclaveAttestationValidator(TrustConfiguration trust, AttestationStatementVerifier statementVerifier) {
        this.trust = new AtomicReference<>(Objects.requireNonNull(trust));
        this.statementVerifier = Objects.requireNonNull(statementVerifier);
    }

    public ValidationResult validate(Attestation attestation) {
        try {
            if (!statementVerifier.verify(attestation.format(), attestation.authenticatorData(), attestation.clientDataHash(), attestation.statement()))
                return new ValidationResult(false, "attestation statement invalid");
            List<X509Certificate> chain = attestation.certificateChain();
            if (chain.isEmpty()) return new ValidationResult(false, "attestation certificate chain missing");
            Instant now = Instant.now();
            for (X509Certificate certificate : chain) certificate.checkValidity(java.util.Date.from(now));
            CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(chain);
            var params = new java.security.cert.PKIXParameters(trust.get().trustAnchors());
            params.setDate(java.util.Date.from(now));
            params.setRevocationEnabled(trust.get().revocationChecking());
            java.security.cert.CertPathValidator.getInstance("PKIX").validate(path, params);
            return new ValidationResult(true, "validated");
        } catch (Exception invalid) {
            return new ValidationResult(false, invalid.getClass().getSimpleName());
        }
    }

    public void reloadTrust(TrustConfiguration replacement) { trust.set(Objects.requireNonNull(replacement)); }

    public record Attestation(String format, byte[] authenticatorData, byte[] clientDataHash,
            byte[] statement, List<X509Certificate> certificateChain) {
        public Attestation {
            if (format == null || format.isBlank()) throw new IllegalArgumentException("format required");
            authenticatorData = authenticatorData.clone(); clientDataHash = clientDataHash.clone(); statement = statement.clone();
            certificateChain = List.copyOf(certificateChain);
        }
        @Override public byte[] authenticatorData() { return authenticatorData.clone(); }
        @Override public byte[] clientDataHash() { return clientDataHash.clone(); }
        @Override public byte[] statement() { return statement.clone(); }
    }
    public record TrustConfiguration(Set<java.security.cert.TrustAnchor> trustAnchors, boolean revocationChecking) {
        public TrustConfiguration { trustAnchors = Set.copyOf(trustAnchors); if (trustAnchors.isEmpty()) throw new IllegalArgumentException("trust anchors required"); }
    }
    public record ValidationResult(boolean valid, String reason) {}
    @FunctionalInterface public interface AttestationStatementVerifier {
        boolean verify(String format, byte[] authenticatorData, byte[] clientDataHash, byte[] statement) throws Exception;
    }
}
