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
