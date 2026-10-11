import type { KeyCategory, KeystrokeFeature } from "../types/telemetry";

const categoryFor = (key: string): KeyCategory => {
  if (/^[a-z]$/i.test(key)) return "ALPHA";
  if (/^[0-9]$/.test(key)) return "NUMERIC";
  if (key === " ") return "SPACE";
  if (key === "Backspace" || key === "Delete") return "DELETE";
  if (key.startsWith("Arrow") || ["Tab", "Home", "End", "PageUp", "PageDown", "Enter"].includes(key)) return "NAV";
  return "MODIFIER";
};

export class KeystrokeHarvester {
  private readonly pressed = new Map<KeyCategory, number>();
  private lastRelease: number | null = null;
  private readonly features: KeystrokeFeature[] = [];
  private readonly onKeyDown = (event: KeyboardEvent): void => {
    const now = performance.now();
    const category = categoryFor(event.key);
    if (!this.pressed.has(category)) this.pressed.set(category, now);
  };
  private readonly onKeyUp = (event: KeyboardEvent): void => {
    const now = performance.now();
    const category = categoryFor(event.key);
    const press = this.pressed.get(category);
    if (press === undefined) return;
    this.pressed.delete(category);
    this.features.push({
      category,
      dwellMs: Math.max(0, now - press),
      flightMs: this.lastRelease === null ? null : Math.max(0, press - this.lastRelease)
    });
    this.lastRelease = now;
  };

  start(): void {
    window.addEventListener("keydown", this.onKeyDown, { passive: true });
    window.addEventListener("keyup", this.onKeyUp, { passive: true });
  }

  stop(): void {
    window.removeEventListener("keydown", this.onKeyDown);
    window.removeEventListener("keyup", this.onKeyUp);
    this.pressed.clear();
  }

  drain(): KeystrokeFeature[] {
    return this.features.splice(0, this.features.length);
  }
}
