export type RiskTier = "LOW" | "MEDIUM" | "HIGH";
export type KeyCategory = "ALPHA" | "NUMERIC" | "MODIFIER" | "NAV" | "SPACE" | "DELETE";

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

export interface TelemetryBatch {
  seq_id: number;
  observedAt: number;
  keystrokes: readonly KeystrokeFeature[];
  mouse: readonly MouseFeature[];
  environment: {
    localeOffsetMinutes: number;
    userAgentFamily: string;
  };
}

export interface BranchScores {
  network: number;
  biometric: number;
  variance: number;
}

export interface RiskEvent {
  id: string;
  sequence: number;
  timestamp: string;
  score: number;
  tier: RiskTier;
  branches: BranchScores;
  auditId?: string;
}

export interface WebAuthnChallenge {
  challengeId: string;
  publicKey: PublicKeyCredentialRequestOptions;
}

export interface TelemetryTransport {
  send(batch: TelemetryBatch): void;
  close(): void;
}
