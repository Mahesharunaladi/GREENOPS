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

## Email-verified sign-in

Registration and sign-in send a six-digit code to the submitted email address. Codes expire after 10 minutes; a verified session is kept in an HTTP-only cookie for up to 8 hours. SMTP settings are loaded automatically from the ignored `BACKEND/.env` file whether Maven is started from the repository root or from `BACKEND`. Enter the SMTP password once in that file; subsequent backend restarts load it without repeating `export` commands:

```properties
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-gmail-address
GREENOPS_SMTP_PASSWORD=your-new-google-app-password-without-spaces
SMTP_AUTH=true
SMTP_STARTTLS=true
GREENOPS_AUTH_MAIL_FROM=your-gmail-address
GREENOPS_AUTH_COOKIE_SECURE=false
```

The previous App Password was exposed and must be revoked. Generate a replacement once and put it in the ignored `BACKEND/.env` file; do not commit or share that file. `GREENOPS_SMTP_PASSWORD` takes precedence over a stale `SMTP_PASSWORD` exported in the terminal.

The account, verification-code, and session store is currently in memory for development. It is cleared when the backend restarts and is not suitable for production; replace it with a durable user/session store and configure HTTPS before deploying.

## SPIRE workload attestation

`POST /api/v1/spire/attest` accepts `WorkloadTelemetry` JSON and is protected by the verified GreenOps session filter. The console's **SPIRE** badge reads `GET /api/v1/spire/status`, showing inactive/configured/recent request activity plus missing integration checks. “Recent activity” means an attestation request reached this endpoint in the last minute; it is not a background process monitor. Configure `GREENOPS_SPIRE_TRUST_DOMAIN`, an X.509 trust anchor PEM (`GREENOPS_SPIRE_X509_TRUST_ANCHOR_PEM`) or JWT RSA public key (`GREENOPS_SPIRE_JWT_RSA_PUBLIC_KEY`, base64 DER), `GREENOPS_SPIRE_JWT_AUDIENCE`, and comma-separated trusted node hashes (`GREENOPS_SPIRE_TRUSTED_NODE_HASHES`). Process allowlists can be global (`GREENOPS_SPIRE_ALLOWED_BINARIES`, comma-separated) or per-user (`GREENOPS_SPIRE_USER_PROCESS_PROFILES`, for example `alice=mvn|kubectl;bob=java|gradle`). Successful executions from the allowlist are counted in a concurrent in-memory history cache and are lost on restart.

The default biometric, SPIRE issuance/revocation, and IAM identity-mapping adapters are deliberately disabled. Connect `BiometricSignalProvider` to trusted `ZeroTrustAgent` session results, `SpireSvidIssuer` and `SpireRevocationClient` to the deployment's SPIRE Workload/Server APIs, and `IamPrincipalResolver` to a server-side session-to-principal mapping before enabling enforcement. Until those adapters are configured, this endpoint fails closed; it does not claim successful attestation or apply IAM policy based on a request-supplied user ID. CloudWatch metrics use namespace `GreenOps/SPIRE`.

## Deception sandbox

The deception router sends requests with a trusted blacklisted fingerprint or trusted server-side `UserSessionContext` failure to `/api/v1/sandbox/synthetic-data`. The console's **Honeypot Sandbox** tab reads authenticated `GET /api/v1/sandbox/events` and displays recent action records. Synthetic EC2 stop and S3 audit routes only return fake data. Action records are bounded in memory by default; `ReactiveThreatRepository` is the hook for a durable reactive/DynamoDB implementation. Captured data is capped at 8 KiB and credential headers/fields are redacted. Configure `GREENOPS_DECEPTION_TRUSTED_PROXIES` with exact proxy IPs before accepting `X-Forwarded-For`, and configure the ingress to strip client-provided `X-TLS-JA3-Fingerprint` before setting the observed TLS fingerprint. The default routing signals require an upstream trusted component to place `UserSessionContext` in request attributes. CloudWatch uses namespace `GreenOps/Deception`, metric `greenops.deception.trapped_attackers`.

## Enterprise governance and canary workflows

The enterprise API is under `/api/v1/enterprise`. Administrative endpoints require `X-GreenOps-Admin-Key`, configured with a strong `GREENOPS_ENTERPRISE_ADMIN_API_KEY` of at least 32 bytes, in addition to the normal signed-in session. `GET /compliance/score` uses `?awsAccountId=...` or `GREENOPS_AWS_ACCOUNT_ID` and inspects S3 Public Access Block configuration, EBS/RDS encryption, and root account MFA in the configured AWS region. AWS credentials need read permissions for those checks. Evaluation errors from AWS permissions or unavailable services are returned as errors, not as a synthetic score. S3 Public Access Block remediation is implemented in the service; EBS/RDS remediation is not automatic.

`GET /blast-radius/{userId}?iamRoleArn=...` uses IAM policy simulation and resource inventories. Scanning is capped at 100 resources per type. The USD exposure number is an estimate using `GREENOPS_RISK_EXPOSURE_S3_BUCKET`, `GREENOPS_RISK_EXPOSURE_RDS_DATABASE`, and `GREENOPS_RISK_EXPOSURE_EC2_INSTANCE`; it is not a measured financial loss. IAM simulation requires `iam:SimulatePrincipalPolicy`, role policy listing, and resource inventory permissions.

`POST /caep/revoke-session` creates an HS256-signed CAEP session-revoked SET and sends it to the configured webhook and/or EventBridge bus. Set `GREENOPS_CAEP_HMAC_SECRET` to a strong secret of at least 32 bytes and configure `GREENOPS_CAEP_WEBHOOK` or `GREENOPS_ENTERPRISE_EVENT_BUS`. The receiving identity-provider tenant must trust the issuer/key and consume this CAEP event; delivery alone does not force logout at Okta, Entra ID, or Google Workspace.

Synthetic S3 audit CSV responses include an inert, invalid AWS credential marker. Configure `GREENOPS_CANARY_CALLBACK_URL` with the public callback base URL and `GREENOPS_CANARY_ALERT_WEBHOOK` and/or `GREENOPS_ENTERPRISE_EVENT_BUS` for alerts. Callback URLs are random bearer capabilities and bypass browser-session authentication; serve them only over HTTPS and protect them at the edge. Canary records and callback state use a bounded in-memory cache and are lost at restart. The marker cannot detect use through AWS authentication itself; the callback detects visits to the URL embedded in synthetic data.

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
