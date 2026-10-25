# GREENOPS
GreenOps Cost-Optimizer is an automated FinOps/GreenOps system that runs nightly to detect idle or wasteful AWS resources, estimate the cost leakage, automatically stop/terminate the resources (per policy), and post a summary report to Slack

GreenOps scans your AWS estate every night, finds the idle instances and forgotten dev boxes quietly burning budget, and shuts them down — safely, with guardrails, and a paper trail for every decision.

# GreenOps frontend

This folder contains the production frontend foundation:

- `apps/soc-dashboard`: Next.js 14 App Router SOC dashboard
- `packages/sdk`: privacy-preserving TypeScript telemetry SDK
- The original static dashboard remains available at `index.html` during migration.

## Run the SOC dashboard locally

```bash
npm install
npm run dev
```

The dashboard is available at `http://localhost:3000`. Set `NEXT_PUBLIC_RISK_SSE_URL` only when the backend exposes an authenticated SSE endpoint; otherwise the dashboard uses a clearly labeled demo risk event.

## Validate

```bash
npm run typecheck
npm test
npm run build
```

## SDK privacy boundary

The SDK emits timing aggregates and generic key categories only. It never stores or transmits raw characters, input values, clipboard data, raw pointer coordinates, cookies, or bearer tokens. Worker buffering is bounded and transport falls back to `sendBeacon` on unload.

Browser lockout cleanup is best-effort: accessible Web Storage and IndexedDB databases are removed and active runtime connections should be closed by the host application. Browser JavaScript cannot guarantee cryptographic memory erasure or prevent a privileged user from bypassing a screen.

The existing REST flows remain available in the static dashboard: cost scans, audit history, dry-run remediation, patch/PDF downloads, and policy-gated pull requests.

## Zero Trust innovation modules

The Java 17 backend includes opt-in building blocks under `com.greenops.innovation` for ML-DSA JWS verification, RFC 9449 DPoP request validation, weighted risk consensus, synthetic deception routing, attestation-chain validation, clipped/noised federated updates, and risk-based inference tier selection. These are integration points, not a turnkey identity provider: DPoP proof-signature/JWK verification and replay protection require deployment adapters; the attestation verifier and trust anchors must be supplied from a managed trust bundle; CloudTrail and network reputation observations need trusted AWS/enrichment adapters; and model state needs a durable encrypted store plus a formal privacy accountant before production federation.

DPoP binds a proof key to an access token and HTTP request. It does not bind the token to a TLS session key. The ML-DSA verifier is separate and expects the configured trusted public key to be selected by issuer/key policy. The deception filter uses Spring MVC's servlet filter API (the app is not a reactive WebFlux application); its safe default risk resolver returns low risk until replaced with a trusted server-side session lookup. Never use request headers or client telemetry as the authority for a route-to-honeypot decision.
