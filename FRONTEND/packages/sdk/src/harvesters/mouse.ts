import type { MouseFeature } from "../types/telemetry";

interface Point { x: number; y: number; at: number }

export class MouseHarvester {
  private previous: Point | null = null;
  private downAt: number | null = null;
  private distance = 0;
  private straightDistance = 0;
  private speed = 0;
  private previousSpeed = 0;
  private windowStartedAt: number | null = null;
  private readonly features: MouseFeature[] = [];
  private readonly onMove = (event: MouseEvent): void => {
    const now = performance.now();
    const point: Point = { x: event.clientX, y: event.clientY, at: now };
    if (this.previous === null) { this.previous = point; this.windowStartedAt = now; return; }
    const dt = Math.max(1, now - this.previous.at);
    const distance = Math.hypot(point.x - this.previous.x, point.y - this.previous.y);
    this.distance += distance;
    this.straightDistance = Math.hypot(point.x - this.previous.x, point.y - this.previous.y);
    const nextSpeed = distance / (dt / 1000);
    const acceleration = (nextSpeed - this.speed) / (dt / 1000);
    this.previousSpeed = this.speed;
    this.speed = nextSpeed;
    const windowMs = now - (this.windowStartedAt ?? this.previous.at);
    if (windowMs >= 100) {
      const curvature = this.straightDistance > 0 ? this.distance / this.straightDistance : 1;
      this.features.push({ windowMs, velocity: nextSpeed, acceleration, curvature: Math.max(1, curvature), clickDwellMs: null });
      this.distance = 0;
      this.straightDistance = 0;
      this.windowStartedAt = now;
    }
    this.previous = point;
  };
  private readonly onDown = (event: MouseEvent): void => {
    this.downAt = performance.now();
    this.previous = { x: event.clientX, y: event.clientY, at: this.downAt };
  };
  private readonly onUp = (): void => {
    if (this.downAt === null) return;
    const dwell = Math.max(0, performance.now() - this.downAt);
    this.features.push({ windowMs: 0, velocity: this.speed, acceleration: this.speed - this.previousSpeed, curvature: 1, clickDwellMs: dwell });
    this.downAt = null;
  };

  start(): void {
    window.addEventListener("mousemove", this.onMove, { passive: true });
    window.addEventListener("mousedown", this.onDown, { passive: true });
    window.addEventListener("mouseup", this.onUp, { passive: true });
    window.addEventListener("touchmove", this.onTouchMove, { passive: true });
  }
  stop(): void {
    window.removeEventListener("mousemove", this.onMove);
    window.removeEventListener("mousedown", this.onDown);
    window.removeEventListener("mouseup", this.onUp);
    window.removeEventListener("touchmove", this.onTouchMove);
    this.previous = null;
    this.windowStartedAt = null;
    this.downAt = null;
  }
  drain(max = 64): MouseFeature[] { return this.features.splice(0, Math.max(0, max)); }

  private readonly onTouchMove = (event: TouchEvent): void => {
    const touch = event.touches[0];
    if (touch === undefined) return;
    this.recordPoint(touch.clientX, touch.clientY);
  };

  private recordPoint(x: number, y: number): void {
    const now = performance.now();
    const point: Point = { x, y, at: now };
    if (this.previous === null) { this.previous = point; this.windowStartedAt = now; return; }
    const dt = Math.max(1, now - this.previous.at);
    const distance = Math.hypot(point.x - this.previous.x, point.y - this.previous.y);
    this.distance += distance;
    this.straightDistance = Math.hypot(point.x - this.previous.x, point.y - this.previous.y);
    const nextSpeed = distance / (dt / 1000);
    this.previousSpeed = this.speed;
    this.speed = nextSpeed;
    const windowMs = now - (this.windowStartedAt ?? this.previous.at);
    if (windowMs >= 100) {
      const curvature = this.straightDistance > 0 ? this.distance / this.straightDistance : 1;
      this.features.push({ windowMs, velocity: nextSpeed, acceleration: (nextSpeed - this.previousSpeed) / (dt / 1000), curvature: Math.max(1, curvature), clickDwellMs: null });
      this.distance = 0;
      this.straightDistance = 0;
      this.windowStartedAt = now;
    }
    this.previous = point;
  }
}
