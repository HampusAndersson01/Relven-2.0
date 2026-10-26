package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class WorkoutTelemetryProtocolTest {
  private static final String SESSION = "telemetry-session-1";

  @Test
  public void roundTripsGpsOnlyHeartRateOnlyAndCombinedSamples() throws Exception {
    WorkoutTelemetryProtocol.Sample gps =
        new WorkoutTelemetryProtocol.Sample(
            SESSION, 1, 100, 1_000, 59.3, 18.0, 6.0, null, null, null);
    WorkoutTelemetryProtocol.Sample heartRate =
        new WorkoutTelemetryProtocol.Sample(
            SESSION, 2, 200, 1_100, null, null, null, null, null, 142.0);
    WorkoutTelemetryProtocol.Sample combined =
        new WorkoutTelemetryProtocol.Sample(
            SESSION, 3, 300, 1_200, 59.4, 18.1, 5.0, 12.0, 2.5, 150.0);

    WorkoutTelemetryProtocol.Batch batch =
        new WorkoutTelemetryProtocol.Batch(SESSION, Arrays.asList(gps, heartRate, combined));
    WorkoutTelemetryProtocol.Batch decoded =
        WorkoutTelemetryProtocol.decodeBatch(WorkoutTelemetryProtocol.encodeBatch(batch));

    assertEquals(batch, decoded);
    assertEquals(
        new WorkoutTelemetryProtocol.Ack(SESSION, 2),
        WorkoutTelemetryProtocol.decodeAck(
            WorkoutTelemetryProtocol.encodeAck(new WorkoutTelemetryProtocol.Ack(SESSION, 2))));
  }

  @Test
  public void rejectsEmptyOversizedAndNonContiguousBatches() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.Batch(SESSION, Collections.emptyList()));
    WorkoutTelemetryProtocol.Sample first = sample(1);
    WorkoutTelemetryProtocol.Sample third = sample(3);
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.Batch(SESSION, Arrays.asList(first, third)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.Batch(SESSION, Collections.nCopies(65, first)));
  }

  @Test
  public void rejectsMalformedVersionPayloadAndInvalidMeasurements() throws Exception {
    byte[] encoded =
        WorkoutTelemetryProtocol.encodeBatch(
            new WorkoutTelemetryProtocol.Batch(SESSION, Collections.singletonList(sample(1))));
    encoded[4] = (byte) 99;
    assertThrows(IOException.class, () -> WorkoutTelemetryProtocol.decodeBatch(encoded));
    assertThrows(IOException.class, () -> WorkoutTelemetryProtocol.decodeBatch(new byte[0]));
    assertThrows(
        IOException.class,
        () ->
            WorkoutTelemetryProtocol.decodeBatch(
                new byte[WorkoutTelemetryProtocol.MAX_PAYLOAD_BYTES + 1]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WorkoutTelemetryProtocol.Sample(
                SESSION, 1, 1, 1, 91.0, 18.0, 5.0, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WorkoutTelemetryProtocol.Sample(
                SESSION, 1, 1, 1, null, null, 5.0, null, null, null));
  }

  @Test
  public void sampleIdentityIncludesSessionAndSequence() {
    assertNotEquals(sample(1), sample(2));
    assertEquals(sample(1), sample(1));
  }

  @Test
  public void v1BatchRemainsReadableWithoutClockAnchor() throws Exception {
    WorkoutTelemetryProtocol.Sample sample = sample(1);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(0x57544231);
      output.writeByte(1);
      output.writeUTF(SESSION);
      output.writeByte(1);
      output.writeLong(sample.sequence);
      output.writeLong(sample.watchElapsedRealtimeNanos);
      output.writeLong(sample.wallTimeMillis);
      output.writeByte(3);
      output.writeDouble(sample.latitude);
      output.writeDouble(sample.longitude);
      output.writeDouble(sample.horizontalAccuracyMeters);
    }

    WorkoutTelemetryProtocol.Batch decoded =
        WorkoutTelemetryProtocol.decodeBatch(bytes.toByteArray());

    assertEquals(Collections.singletonList(sample), decoded.samples);
    assertNull(decoded.clockAnchor);
  }

  @Test
  public void v2BatchCarriesBoundedClockAnchorWithoutChangingSampleIdentity() throws Exception {
    WorkoutTelemetryProtocol.Sample sample = sample(1);
    WorkoutTelemetryProtocol.ClockAnchor anchor =
        new WorkoutTelemetryProtocol.ClockAnchor(600_000, 60_000_000_000L, 1_800_000_660_000L, 50);
    WorkoutTelemetryProtocol.Batch batch =
        new WorkoutTelemetryProtocol.Batch(SESSION, Collections.singletonList(sample), anchor);

    WorkoutTelemetryProtocol.Batch decoded =
        WorkoutTelemetryProtocol.decodeBatch(WorkoutTelemetryProtocol.encodeBatch(batch));

    assertEquals(batch, decoded);
    assertEquals(sample, decoded.samples.get(0));
    assertEquals(
        1_800_000_001_000L,
        decoded
            .clockAnchor
            .phoneAlignedWallTimeMillis(
                new WorkoutTelemetryProtocol.Sample(
                    SESSION,
                    1,
                    1_000_000_000L,
                    1_800_000_601_000L,
                    sample.latitude,
                    sample.longitude,
                    sample.horizontalAccuracyMeters,
                    sample.altitudeMeters,
                    sample.speedMetersPerSecond,
                    sample.heartRateBpm))
            .longValue());
  }

  @Test
  public void clockAnchorRejectsUnboundedOffsetRoundTripAndBootMismatch() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.ClockAnchor(86_400_001, 1, 1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkoutTelemetryProtocol.ClockAnchor(0, 1, 1, 2_001));

    WorkoutTelemetryProtocol.ClockAnchor anchor =
        new WorkoutTelemetryProtocol.ClockAnchor(0, 60_000_000_000L, 1_800_000_060_000L, 50);
    WorkoutTelemetryProtocol.Sample rebooted =
        new WorkoutTelemetryProtocol.Sample(
            SESSION, 1, 1_000_000_000L, 1_800_001_001_000L, 59.3, 18.0, 6.0, null, null, null);

    assertNull(anchor.phoneAlignedWallTimeMillis(rebooted));
  }

  private static WorkoutTelemetryProtocol.Sample sample(long sequence) {
    return new WorkoutTelemetryProtocol.Sample(
        SESSION, sequence, sequence * 100, sequence * 1_000, 59.3, 18.0, 6.0, null, null, null);
  }
}
