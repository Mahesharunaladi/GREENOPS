export type RiskTier = "LOW" | "MEDIUM" | "HIGH";
export type KeyCategory = "ALPHA" | "NUMERIC" | "MODIFIER" | "NAV" | "SPACE" | "DELETE";
export type TelemetryFeature = KeystrokeFeature | MouseFeature;

export interface KeystrokeFeature {
  category: KeyCategory;
  dwellMs: number;
  flightMs: number | null;
}

export interface MouseFeature {
  windowMs: number;
  velocity: number;
  acceleration: number;
  curvature: number;
  clickDwellMs: number | null;
}

export interface ClientEnvironment {
  userAgentFamily: string;
  webglRenderer: string | null;
  localeOffsetMinutes: number;
  language: string;
  screen: { width: number; height: number; devicePixelRatio: number };
}

export interface TelemetryBatch {
  seq_id: number;
  observedAt: number;
  keystrokes: readonly KeystrokeFeature[];
  mouse: readonly MouseFeature[];
  sessionId: string;
  environment: ClientEnvironment;
}

export interface TelemetryWorkerConfig {
  type: "configure";
  sessionId: string;
  maxBatches: number;
  flushIntervalMs: number;
  environment: ClientEnvironment;
}

export interface TelemetryWorkerFlush {
  type: "flush";
}

export interface TelemetryWorkerFeature {
  type: "feature";
  feature: TelemetryFeature;
}

export interface TelemetryWorkerTerminate {
  type: "terminate";
}

export type TelemetryWorkerMessage =
  | TelemetryWorkerConfig
  | TelemetryWorkerFlush
  | TelemetryWorkerFeature
  | TelemetryWorkerTerminate;

export interface TelemetryWorkerBatch {
  type: "batch";
  batch: TelemetryBatch;
}

export interface TelemetryWorkerReady {
  type: "ready";
}

export type TelemetryWorkerResponse = TelemetryWorkerBatch | TelemetryWorkerReady;

export interface BranchScores {
  network: number;
  biometric: number;
  variance: number;
}

export interface FingerprintComparison {
  baselineJa3: string;
  currentJa3: string;
  baselineJa4: string;
  currentJa4: string;
  discrepancies: readonly string[];
}

export interface BiometricProfilePoint {
  metric: string;
  baseline: number;
  current: number;
}

export interface RiskBranches extends BranchScores {
  fingerprint?: FingerprintComparison;
  biometricProfile?: readonly BiometricProfilePoint[];
}

export interface RiskEvent {
  id: string;
  sequence: number;
  timestamp: string;
  score: number;
  tier: RiskTier;
  branches: RiskBranches;
  auditId?: string;
}

export type AuditActionStatus = "SUCCESS" | "PENDING" | "FAILED";

export interface AuditEntry {
  id: string;
  action: "PutUserPolicy" | "STS credential invalidation" | "EventBridge CAEP";
  detail: string;
  timestamp: string;
  status: AuditActionStatus;
}

export interface WebAuthnChallenge {
  challengeId: string;
  publicKey: PublicKeyCredentialRequestOptions;
}

export interface WebAuthnResult {
  challengeId: string;
  verified: boolean;
  error?: string;
}

export interface TelemetryTransportOptions {
  websocketUrl: string;
  beaconUrl: string;
  sessionToken?: string;
  maxPendingBatches?: number;
}

export interface TelemetryTransport {
  send(batch: TelemetryBatch): void;
  close(): void;
}

export const riskTierForScore = (score: number): RiskTier => {
  if (score >= 0.75) return "HIGH";
  if (score >= 0.4) return "MEDIUM";
  return "LOW";
};

export const isRiskEvent = (value: unknown): value is RiskEvent => {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Partial<RiskEvent>;
  const branches = candidate.branches;
  return typeof candidate.id === "string"
    && typeof candidate.sequence === "number"
    && typeof candidate.timestamp === "string"
    && typeof candidate.score === "number"
    && candidate.score >= 0
    && candidate.score <= 1
    && (candidate.tier === "LOW" || candidate.tier === "MEDIUM" || candidate.tier === "HIGH")
    && typeof branches === "object"
    && branches !== null
    && typeof branches.network === "number"
    && typeof branches.biometric === "number"
    && typeof branches.variance === "number";
};
