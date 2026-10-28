package com.greenops.workflow.spire;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validates supported X.509 and RS256 JWT SVIDs against explicitly configured trust material. */
@Component
public final class SpiffeSvidValidator {
    private final ObjectMapper mapper;
    private final String trustDomain;
    private final String trustAnchorPem;
    private final String jwtSigningKey;
    private final String jwtAudience;

    public SpiffeSvidValidator(
            ObjectMapper mapper,
            @Value("${greenops.spire.trust-domain:greenops.internal}") String trustDomain,
            @Value("${greenops.spire.trust-anchor-pem:}") String trustAnchorPem,
            @Value("${greenops.spire.jwt-rsa-public-key:}") String jwtSigningKey,
            @Value("${greenops.spire.jwt-audience:greenops-api}") String jwtAudience) {
        this.mapper = Objects.requireNonNull(mapper);
        this.trustDomain = trustDomain;
        this.trustAnchorPem = trustAnchorPem;
        this.jwtSigningKey = jwtSigningKey;
        this.jwtAudience = jwtAudience;
    }

    public Validation validate(String encodedSvid) {
        if (encodedSvid == null || encodedSvid.isBlank()) return Validation.invalid("SVID is missing");
        try {
            if (encodedSvid.startsWith("-----BEGIN CERTIFICATE-----")) return validateX509(encodedSvid);
            if (encodedSvid.split("\\.", -1).length == 3) return validateJwt(encodedSvid);
            return Validation.invalid("Expected a PEM X.509 SVID or a signed JWT SVID");
        } catch (Exception ex) {
            return Validation.invalid("SVID cryptographic validation failed");
        }
    }

    public boolean isConfigured() {
        return (trustAnchorPem != null && !trustAnchorPem.isBlank())
                || (jwtSigningKey != null && !jwtSigningKey.isBlank());
    }

    private Validation validateX509(String pem) throws Exception {
        if (trustAnchorPem == null || trustAnchorPem.isBlank()) return Validation.invalid("SPIRE X.509 trust anchor is not configured");
        var factory = CertificateFactory.getInstance("X.509");
        var leaf = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        var anchor = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(trustAnchorPem.getBytes(StandardCharsets.US_ASCII)));
        leaf.checkValidity();
        leaf.verify(anchor.getPublicKey());
        if (leaf.equals(anchor)) return Validation.invalid("A trust anchor cannot be used as a workload SVID");
        var sans = leaf.getSubjectAlternativeNames();
        if (sans != null) for (var san : sans) {
            if (san.size() > 1 && Integer.valueOf(6).equals(san.get(0)) && san.get(1) instanceof String uri && validSpiffeId(uri)) {
                return Validation.valid(uri);
            }
        }
        return Validation.invalid("X.509 SVID has no SPIFFE URI SAN in the configured trust domain");
    }

    private Validation validateJwt(String token) throws Exception {
        if (jwtSigningKey == null || jwtSigningKey.isBlank()) return Validation.invalid("SPIRE JWT verification key is not configured");
        String[] parts = token.split("\\.", -1);
        Map<?, ?> header = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
        Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
        if (!"RS256".equals(header.get("alg"))) return Validation.invalid("Only RS256 JWT SVIDs are accepted");
        byte[] keyBytes = Base64.getDecoder().decode(jwtSigningKey);
        var publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) return Validation.invalid("JWT SVID signature is invalid");
        Object sub = claims.get("sub");
        Object exp = claims.get("exp");
        Object iat = claims.get("iat");
        Object aud = claims.get("aud");
        if (!(sub instanceof String spiffeId) || !validSpiffeId(spiffeId)) return Validation.invalid("JWT SVID subject is outside the configured trust domain");
        if (!(exp instanceof Number expiry) || Instant.now().getEpochSecond() >= expiry.longValue()) return Validation.invalid("JWT SVID is expired or has no expiry");
        if (!(iat instanceof Number issuedAt) || issuedAt.longValue() > Instant.now().plusSeconds(30).getEpochSecond()) return Validation.invalid("JWT SVID is missing a valid issued-at claim");
        boolean audienceMatches = aud instanceof String value && jwtAudience.equals(value)
                || aud instanceof java.util.Collection<?> values && values.contains(jwtAudience);
        if (!audienceMatches) return Validation.invalid("JWT SVID audience does not match this service");
        return Validation.valid(spiffeId);
    }

    private boolean validSpiffeId(String value) {
        return value != null && value.startsWith("spiffe://" + trustDomain + "/")
                && value.length() > ("spiffe://" + trustDomain + "/").length()
                && !value.contains("?") && !value.contains("#");
    }

    public record Validation(boolean valid, String spiffeId, String reason) {
        static Validation valid(String id) { return new Validation(true, id, "SVID signature and trust domain verified"); }
        static Validation invalid(String reason) { return new Validation(false, "unknown", reason); }
    }
}
