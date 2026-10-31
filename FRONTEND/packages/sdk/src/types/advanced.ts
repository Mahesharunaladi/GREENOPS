/** Request contract for the backend agentic prompt inspection endpoint. */
export interface AgenticPromptPayload {
  promptId: string;
  agentId: string;
  userPrompt: string;
  targetToolCall: string;
  detectedInjectionSignatures: string[];
  containsIndirectInjection: boolean;
}

export interface PreAttackSnapshot {
  snapshotId: string;
  resourceArn: string;
  originalConfigJson: string;
  capturedAt: string;
}

export interface TtcRiskAssessment {
  userId: string;
  timeToCompromiseMinutes: number;
  exposureProbabilityPercentage: number;
  primaryRiskFactor: string;
  predictedAt: string;
}

export type AttackStorylineStage =
  | "INITIAL_AUTH"
  | "BIOMETRIC_ANOMALY"
  | "SPIRE_REVOCATION"
  | "DECEPTION_REROUTE"
  | "HONEYPOT_EXPLOIT";

export interface AttackStorylineEvent {
  eventId: string;
  sessionId: string;
  stage: AttackStorylineStage;
  actionDetails: string;
  sourceIp: string;
  timestamp: string;
}

/** In-memory form state for the advanced security console. */
export interface AdvancedSecurityFormState {
  adminKey: string;
  promptId: string;
  agentId: string;
  userPrompt: string;
  targetToolCall: string;
  resourceArn: string;
  userId: string;
  sessionId: string;
}
