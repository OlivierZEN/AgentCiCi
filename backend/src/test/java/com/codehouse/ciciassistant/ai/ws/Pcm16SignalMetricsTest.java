package com.codehouse.ciciassistant.ai.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class Pcm16SignalMetricsTest {

    @Test
    void reportsSilenceWithoutRetainingAudioContent() {
        Pcm16SignalMetrics metrics = new Pcm16SignalMetrics();
        metrics.observe(ByteBuffer.allocate(3200));

        assertThat(metrics.sampleCount()).isEqualTo(1600);
        assertThat(metrics.peakRatio()).isZero();
        assertThat(metrics.rmsRatio()).isZero();
    }

    @Test
    void measuresLittleEndianPcmAmplitude() {
        ByteBuffer frame = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) 0).putShort((short) 16384).putShort((short) -16384).putShort((short) 0).flip();
        Pcm16SignalMetrics metrics = new Pcm16SignalMetrics();
        metrics.observe(frame);

        assertThat(metrics.sampleCount()).isEqualTo(4);
        assertThat(metrics.peakRatio()).isEqualTo(0.5);
        assertThat(metrics.rmsRatio()).isCloseTo(Math.sqrt(0.125), within(0.000001));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
