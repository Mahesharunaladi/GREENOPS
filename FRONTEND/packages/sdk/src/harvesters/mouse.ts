import type { MouseFeature } from "../types/telemetry";

interface Point { x: number; y: number; at: number }

export class MouseHarvester {
  private previous: Point | null = null;
  private downAt: number | null = null;
  private distance = 0;
  private speed = 0;
  private previousSpeed = 0;
  private readonly features: MouseFeature[] = [];
  private readonly onMove = (event: MouseEvent): void => {
    const now = performance.now();
    const point: Point = { x: event.clientX, y: event.clientY, at: now };
    if (this.previous === null) { this.previous = point; return; }
    const dt = Math.max(1, now - this.previous.at);
    const distance = Math.hypot(point.x - this.previous.x, point.y - this.previous.y);
    this.distance += distance;
    const nextSpeed = distance / (dt / 1000);
    const acceleration = (nextSpeed - this.speed) / (dt / 1000);
    this.previousSpeed = this.speed;
    this.speed = nextSpeed;
    if (now - this.previous.at >= 100) {
      this.features.push({ windowMs: dt, velocity: nextSpeed, acceleration, curvature: 1, clickDwellMs: null });
      this.distance = 0;
    }
    this.previous = point;
  };
  private readonly onDown = (): void => { this.downAt = performance.now(); };
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
  }
  stop(): void {
    window.removeEventListener("mousemove", this.onMove);
    window.removeEventListener("mousedown", this.onDown);
    window.removeEventListener("mouseup", this.onUp);
  }
  drain(): MouseFeature[] { return this.features.splice(0, this.features.length); }
}
