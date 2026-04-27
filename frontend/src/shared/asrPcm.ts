/** Float32 PCM → Int16 little-endian for Aliyun-style ASR binary frames. */
export function floatTo16BitPcm(float32: Float32Array): ArrayBuffer {
  const out = new Int16Array(float32.length);
  for (let index = 0; index < float32.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, float32[index]));
    out[index] = sample < 0 ? sample * 0x8000 : sample * 0x7fff;
  }
  return out.buffer;
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
