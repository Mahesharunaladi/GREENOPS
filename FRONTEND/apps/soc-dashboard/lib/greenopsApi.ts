export interface BackendScanFinding {
  resourceId: string;
  region: string;
  reason: string;
  action: string;
  actionTaken: boolean;
  savingsEstimate: { currency: string; hourlyRate: number; estimatedMonthlySavings: number } | null;
  tags: Record<string, string>;
}

export interface BackendScanResult {
  scannedAt: string;
  dryRun: boolean;
  totalResources: number;
  idleResources: number;
  totalEstimatedMonthlySavings: number;
  findings: readonly BackendScanFinding[];
  slackSummary: string | null;
}

export interface BackendSnapshot {
  scan: BackendScanResult | null;
  auditCount: number;
  recentTelemetry: readonly TelemetryMetricPoint[];
  telemetry: {
    acceptedBatches: number;
    retainedBatches: number;
    latestSequence: number | null;
    latestDwellMeanMs: number;
    latestFlightMeanMs: number;
    latestMouseVelocityMean: number;
    latestMouseCurvatureMean: number;
  };
  connected: boolean;
  loading: boolean;
  error: string | null;
}

export interface TelemetryMetricPoint {
  sequence: number;
  receivedAt: string;
  keystrokeCount: number;
  mouseFeatureCount: number;
  dwellMeanMs: number;
  flightMeanMs: number;
  mouseVelocityMean: number;
  mouseCurvatureMean: number;
}

const backendOrigin = (): string => process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

const requestJson = async <T>(path: string, signal: AbortSignal): Promise<T> => {
  const response = await fetch(`${backendOrigin()}${path}`, {
    method: "GET",
    credentials: "include",
    headers: { accept: "application/json" },
    signal
  });
  if (!response.ok) throw new Error(`GREENOPS API returned HTTP ${response.status}`);
  return response.json() as Promise<T>;
};

export const getBackendSnapshot = async (signal: AbortSignal): Promise<Pick<BackendSnapshot, "scan" | "auditCount" | "telemetry" | "recentTelemetry">> => {
  const [scan, audits, telemetry, recentTelemetry] = await Promise.all([
    requestJson<BackendScanResult>("/api/v1/scan?dryRun=true", signal),
    requestJson<readonly BackendScanResult[]>("/api/v1/audits", signal),
    requestJson<BackendSnapshot["telemetry"]>("/api/v1/telemetry/status", signal),
    requestJson<readonly TelemetryMetricPoint[]>("/api/v1/telemetry/recent", signal)
  ]);
  return { scan, auditCount: audits.length, telemetry, recentTelemetry };
};
