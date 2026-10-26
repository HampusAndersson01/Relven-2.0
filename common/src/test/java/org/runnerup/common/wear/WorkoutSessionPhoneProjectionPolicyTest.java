package org.runnerup.common.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WorkoutSessionPhoneProjectionPolicyTest {
  private static final String SESSION = "6fd8393e-9f90-4c08-88dc-3e98fe52dbd0";
  private static final String COMMAND = "dd198939-8ab0-489d-98d6-625db4a70f67";

  @Test
  public void offlineControlAfterWatchRebootUsesPhoneNowAndKeepsCapturedWallTime() {
    WorkoutSessionProtocol.Command accepted =
        command(WorkoutSessionProtocol.Action.PAUSE, 900_000L, 0L, 1_700_000_000_000L);
    WorkoutSessionProtocol.WallClockOffset wallOffset = wallOffset();

    WorkoutSessionProtocol.Command projection =
        WorkoutSessionPhoneProjectionPolicy.forPhone(accepted, 5_000L, null, false, wallOffset);

    assertNotNull(projection);
    assertEquals(5_000L, projection.getIssuedAtElapsedRealtimeMs());
    assertEquals(1_699_999_998_995L, projection.getIssuedAtWallTimeMillis());
  }

  @Test
  public void controlWaitsForWallClockMappingInsteadOfMisplacingRouteBoundary() {
    WorkoutSessionProtocol.Command accepted =
        command(WorkoutSessionProtocol.Action.PAUSE, 2_000L, 0L, 1_700_000_000_000L);

    assertNull(WorkoutSessionPhoneProjectionPolicy.forPhone(accepted, 5_000L, null, false));
  }

  @Test
  public void validClockOffsetPreservesOriginalMonotonicEventTime() {
    WorkoutSessionProtocol.Command accepted =
        command(WorkoutSessionProtocol.Action.RESUME, 7_000L, 0L, 1_700_000_001_000L);
    WorkoutSessionProtocol.ClockOffset offset =
        WorkoutSessionProtocol.estimateWatchInitiatedClockOffset(
            2_000L, 1_000L, 1_010L, 2_020L, 100L);

    WorkoutSessionProtocol.Command projection =
        WorkoutSessionPhoneProjectionPolicy.forPhone(accepted, 10_000L, offset, true, wallOffset());

    assertNotNull(projection);
    assertEquals(5_995L, projection.getIssuedAtElapsedRealtimeMs());
    assertEquals(1_699_999_999_995L, projection.getIssuedAtWallTimeMillis());
  }

  @Test
  public void startIsNotProjectedWithoutFreshClockMapping() {
    WorkoutSessionProtocol.Command accepted =
        command(WorkoutSessionProtocol.Action.START, 2_000L, 3_000L, 1_700_000_000_000L);

    assertNull(
        WorkoutSessionPhoneProjectionPolicy.forPhone(accepted, 5_000L, null, false, wallOffset()));
  }

  private static WorkoutSessionProtocol.Command command(
      WorkoutSessionProtocol.Action action, long issuedAt, long startAt, long wallTime) {
    return new WorkoutSessionProtocol.Command(
        SESSION,
        COMMAND,
        1L,
        WorkoutSessionProtocol.Actor.WATCH,
        action,
        issuedAt,
        startAt,
        wallTime);
  }

  private static WorkoutSessionProtocol.WallClockOffset wallOffset() {
    return WorkoutSessionProtocol.estimateWatchInitiatedWallClockOffset(
        1_000L,
        1_700_000_000_000L,
        500L,
        1_699_999_999_000L,
        510L,
        1_699_999_999_010L,
        1_020L,
        1_700_000_000_020L,
        250L);
  }
}
