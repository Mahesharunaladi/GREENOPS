import { describe, expect, it } from "vitest";
import { KeystrokeHarvester } from "./keystroke";

describe("KeystrokeHarvester privacy", () => {
  it("does not expose raw keys in its feature output", () => {
    const harvester = new KeystrokeHarvester();
    harvester.start();
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "a" }));
    window.dispatchEvent(new KeyboardEvent("keyup", { key: "a" }));
    const [feature] = harvester.drain();
    harvester.stop();
    expect(feature?.category).toBe("ALPHA");
    expect(JSON.stringify(feature)).not.toContain("\"a\"");
  });
});
