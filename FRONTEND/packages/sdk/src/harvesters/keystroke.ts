import type { KeyCategory, KeystrokeFeature } from "../types/telemetry";

const categoryFor = (code: string): KeyCategory => {
  if (/^Key[A-Z]$/.test(code)) return "ALPHA";
  if (/^Digit[0-9]$|^Numpad[0-9]$/.test(code)) return "NUMERIC";
  if (code === "Space") return "SPACE";
  if (["Backspace", "Delete"].includes(code)) return "DELETE";
  if (/^Arrow/.test(code) || ["Tab", "Home", "End", "PageUp", "PageDown", "Enter"].includes(code)) return "NAV";
  return "MODIFIER";
};

export class KeystrokeHarvester {
  private readonly pressed = new Map<string, { category: KeyCategory; at: number }>();
  private lastRelease: number | null = null;
  private readonly features: KeystrokeFeature[] = [];
  private readonly onKeyDown = (event: KeyboardEvent): void => {
    const now = performance.now();
    const category = categoryFor(event.code);
    if (!this.pressed.has(event.code)) this.pressed.set(event.code, { category, at: now });
  };
  private readonly onKeyUp = (event: KeyboardEvent): void => {
    const now = performance.now();
    const pressed = this.pressed.get(event.code);
    if (pressed === undefined) return;
    this.pressed.delete(event.code);
    this.features.push({
      category: pressed.category,
      dwellMs: Math.max(0, now - pressed.at),
      flightMs: this.lastRelease === null ? null : Math.max(0, pressed.at - this.lastRelease)
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
    this.lastRelease = null;
  }

  drain(max = 128): KeystrokeFeature[] {
    return this.features.splice(0, Math.max(0, max));
  }
}
