import type { MouseFeature } from "../types/telemetry";

interface Point { x: number; y: number; at: number }

export class MouseHarvester {
  private previous: Point | null = null;
  private windowOrigin: Point | null = null;
  private windowStartedAt: number | null = null;
  private downAt: number | null = null;
  private arcLength = 0;
  private speed = 0;
  private previousSpeed = 0;
  private readonly features: MouseFeature[] = [];

  private readonly onMove = (event: MouseEvent): void => { this.recordPoint(event.clientX, event.clientY); };
  private readonly onTouchMove = (event: TouchEvent): void => {
    const touch = event.touches[0];
    if (touch !== undefined) this.recordPoint(touch.clientX, touch.clientY);
  };
  private readonly onDown = (event: MouseEvent): void => {
    this.downAt = performance.now();
    this.recordPoint(event.clientX, event.clientY);
  };
  private readonly onUp = (): void => {
    if (this.downAt === null) return;
    this.features.push({ windowMs: 0, velocity: this.speed, acceleration: this.speed - this.previousSpeed, curvature: 1, clickDwellMs: Math.max(0, performance.now() - this.downAt) });
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
    this.windowOrigin = null;
    this.windowStartedAt = null;
    this.downAt = null;
    this.arcLength = 0;
  }

  drain(max = 64): MouseFeature[] { return this.features.splice(0, Math.max(0, max)); }

  private recordPoint(x: number, y: number): void {
    const now = performance.now();
    const point: Point = { x, y, at: now };
    if (this.previous === null) {
      this.previous = point;
      this.windowOrigin = point;
      this.windowStartedAt = now;
      return;
    }
    const dtMs = Math.max(1, now - this.previous.at);
    const segment = Math.hypot(point.x - this.previous.x, point.y - this.previous.y);
    this.arcLength += segment;
    this.previousSpeed = this.speed;
    this.speed = segment / (dtMs / 1000);
    const elapsed = now - (this.windowStartedAt ?? this.previous.at);
    if (elapsed >= 100) {
      const origin = this.windowOrigin ?? this.previous;
      const straightLine = Math.hypot(point.x - origin.x, point.y - origin.y);
      const acceleration = (this.speed - this.previousSpeed) / (dtMs / 1000);
      this.features.push({ windowMs: elapsed, velocity: this.speed, acceleration, curvature: straightLine > 0 ? Math.max(1, this.arcLength / straightLine) : 1, clickDwellMs: null });
      this.arcLength = 0;
      this.windowOrigin = point;
      this.windowStartedAt = now;
    }
    this.previous = point;
  }
}
