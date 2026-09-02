import { describe, expect, it } from "vitest";
import { normalizeAsrInput } from "./asrPcm";

describe("normalizeAsrInput", () => {
  it("does not turn a silent track into artificial audio", () => {
    const result = normalizeAsrInput(new Float32Array(4096));

    expect(result.audible).toBe(false);
    expect(result.gain).toBe(1);
    expect(result.samples.every((sample) => sample === 0)).toBe(true);
  });

  it("amplifies quiet speech while preserving the original buffer", () => {
    const input = new Float32Array([0.01, -0.012, 0.008, -0.01]);
    const original = Array.from(input);
    const result = normalizeAsrInput(input);

    expect(result.audible).toBe(true);
    expect(result.gain).toBeGreaterThan(1);
    expect(Math.max(...Array.from(result.samples).map(Math.abs))).toBeGreaterThan(0.012);
    expect(Array.from(input)).toEqual(original);
  });

  it("does not boost an already strong signal", () => {
    const input = new Float32Array([0.3, -0.25, 0.2, -0.3]);
    const result = normalizeAsrInput(input);

    expect(result.audible).toBe(true);
    expect(result.gain).toBe(1);
    expect(result.samples).toBe(input);
  });
});
