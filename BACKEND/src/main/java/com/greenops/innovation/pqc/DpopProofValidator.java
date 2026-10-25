package com.greenops.innovation.pqc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** RFC 9449 request and access-token binding checks. TLS exporter binding is a separate deployment profile. */
public final class DpopProofValidator {
    private final ObjectMapper mapper;
    private final ProofSignatureVerifier signatureVerifier;
    private final ReplayStore replayStore;
    private final Duration maxAge;

    public DpopProofValidator(ObjectMapper mapper, ProofSignatureVerifier signatureVerifier, ReplayStore replayStore) {
        this(mapper, signatureVerifier, replayStore, Duration.ofMinutes(5));
    }

    public DpopProofValidator(ObjectMapper mapper, ProofSignatureVerifier signatureVerifier,
            ReplayStore replayStore, Duration maxAge) {
        this.mapper = Objects.requireNonNull(mapper);
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier);
        this.replayStore = Objects.requireNonNull(replayStore);
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) throw new IllegalArgumentException("maxAge must be positive");
        this.maxAge = maxAge;
    }

    public boolean validate(String compactProof, String accessToken, String requestMethod, URI requestUri,
            String tokenJkt, String requiredNonce) {
        try {
            String[] parts = compactProof.split("\\.", -1);
            if (parts.length != 3) return false;
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String algorithm = text(header, "alg");
            if (!"dpop+jwt".equalsIgnoreCase(text(header, "typ"))
                    || !java.util.Set.of("ES256", "EdDSA", "PS256", "RS256").contains(algorithm)) return false;
            JsonNode publicJwk = header.path("jwk");
            if (!publicJwk.isObject() || !signatureVerifier.verify(publicJwk, algorithm, parts[0], parts[1], parts[2])) return false;
            String jti = text(claims, "jti");
            long issued = claims.path("iat").asLong(Long.MIN_VALUE);
            Instant iat = Instant.ofEpochSecond(issued);
            Instant now = Instant.now();
            if (jti.isBlank() || iat.isBefore(now.minus(maxAge)) || iat.isAfter(now.plusSeconds(30))) return false;
            if (!requestMethod.equalsIgnoreCase(text(claims, "htm"))) return false;
            if (!normalizeHtu(requestUri).equals(normalizeHtu(URI.create(text(claims, "htu"))))) return false;
            if (accessToken == null || requestMethod == null || requestUri == null) return false;
            String expectedAth = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(accessToken.getBytes(StandardCharsets.US_ASCII)));
            if (!expectedAth.equals(text(claims, "ath"))) return false;
            if (requiredNonce != null && !requiredNonce.equals(text(claims, "nonce"))) return false;
            if (tokenJkt == null || !tokenJkt.equals(signatureVerifier.jwkThumbprint(publicJwk))) return false;
            return replayStore.claim(tokenJkt, jti, iat.plus(maxAge));
        } catch (Exception invalid) {
            return false;
        }
    }

    private URI normalizeHtu(URI uri) {
        return URI.create(uri.getScheme().toLowerCase() + "://" + uri.getAuthority().toLowerCase() +
                (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.normalize().getRawPath()));
    }
    private String text(JsonNode node, String name) { return node.path(name).asText(""); }

    public interface ProofSignatureVerifier {
        boolean verify(JsonNode publicJwk, String algorithm, String encodedHeader, String encodedPayload, String encodedSignature) throws Exception;
        String jwkThumbprint(JsonNode publicJwk) throws Exception;
    }
    /** Must be backed by a shared atomic store (for example Redis SET NX) in multi-instance deployments. */
    public interface ReplayStore { boolean claim(String keyId, String jti, Instant expiresAt); }
}
