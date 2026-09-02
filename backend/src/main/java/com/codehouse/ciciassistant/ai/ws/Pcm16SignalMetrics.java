package com.codehouse.ciciassistant.ai.ws;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class Pcm16SignalMetrics {

    private long sampleCount;
    private double sumSquares;
    private int peakAbsolute;

    void observe(ByteBuffer pcm16le) {
        ByteBuffer samples = pcm16le.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        while (samples.remaining() >= Short.BYTES) {
            int value = samples.getShort();
            int absolute = value == Short.MIN_VALUE ? 32768 : Math.abs(value);
            peakAbsolute = Math.max(peakAbsolute, absolute);
            sumSquares += (double) value * value;
            sampleCount += 1;
        }
    }

    long sampleCount() {
        return sampleCount;
    }

    double peakRatio() {
        return peakAbsolute / 32768.0;
    }

    double rmsRatio() {
        return sampleCount == 0 ? 0 : Math.sqrt(sumSquares / sampleCount) / 32768.0;
    }
}
