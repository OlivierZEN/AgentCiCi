/** Float32 PCM → Int16 little-endian for Aliyun-style ASR binary frames. */
export function floatTo16BitPcm(float32: Float32Array): ArrayBuffer {
  const out = new Int16Array(float32.length);
  for (let index = 0; index < float32.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, float32[index]));
    out[index] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
  }
  return out.buffer;
}

export type AsrInputFrame = {
  samples: Float32Array;
  peak: number;
  rms: number;
  audible: boolean;
  gain: number;
};

/**
 * Keeps quiet browser microphones usable without amplifying an effectively
 * silent track into noise. The returned metrics contain no audio content.
 */
export function normalizeAsrInput(buffer: Float32Array): AsrInputFrame {
  let peak = 0;
  let sumSquares = 0;
  for (let index = 0; index < buffer.length; index += 1) {
    const sample = Number.isFinite(buffer[index]) ? buffer[index] : 0;
    peak = Math.max(peak, Math.abs(sample));
    sumSquares += sample * sample;
  }
  const rms = buffer.length > 0 ? Math.sqrt(sumSquares / buffer.length) : 0;
  const audible = peak >= 0.006 || rms >= 0.002;
  const gain = audible && rms > 0 && rms < 0.05 ? Math.min(6, 0.06 / rms) : 1;
  if (gain === 1) {
    return { samples: buffer, peak, rms, audible, gain };
  }
  const samples = new Float32Array(buffer.length);
  for (let index = 0; index < buffer.length; index += 1) {
    samples[index] = Math.max(-1, Math.min(1, (buffer[index] ?? 0) * gain));
  }
  return { samples, peak, rms, audible, gain };
}

/**
 * Selects the channel that actually carries the strongest signal. Some
 * multi-channel browser capture devices expose an empty first channel, so
 * always reading channel 0 can produce valid-looking but all-zero PCM frames.
 */
export function normalizeStrongestAsrChannel(channels: readonly Float32Array[]): AsrInputFrame {
  if (channels.length === 0) {
    return normalizeAsrInput(new Float32Array());
  }
  let strongest = normalizeAsrInput(channels[0]);
  for (let index = 1; index < channels.length; index += 1) {
    const candidate = normalizeAsrInput(channels[index]);
    if (candidate.rms > strongest.rms || (candidate.rms === strongest.rms && candidate.peak > strongest.peak)) {
      strongest = candidate;
    }
  }
  return strongest;
}

/** Downsample mono float buffer to 16 kHz Int16 PCM ArrayBuffer. */
export function downsampleTo16k(buffer: Float32Array, inputSampleRate: number): ArrayBuffer {
  const targetRate = 16000;
  if (inputSampleRate <= targetRate) {
    return floatTo16BitPcm(buffer);
  }
  const ratio = inputSampleRate / targetRate;
  const newLength = Math.max(1, Math.round(buffer.length / ratio));
  const result = new Float32Array(newLength);
  let offsetResult = 0;
  let offsetBuffer = 0;
  while (offsetResult < result.length) {
    const nextOffsetBuffer = Math.min(buffer.length, Math.round((offsetResult + 1) * ratio));
    let accum = 0;
    let count = 0;
    for (let index = offsetBuffer; index < nextOffsetBuffer; index += 1) {
      accum += buffer[index];
      count += 1;
    }
    result[offsetResult] = count > 0 ? accum / count : 0;
    offsetResult += 1;
    offsetBuffer = nextOffsetBuffer;
  }
  return floatTo16BitPcm(result);
}
