package org.runnerup.common.wear;

/** Chooses a phone-clock timestamp for a durable watch session event. */
public final class WorkoutSessionPhoneProjectionPolicy {
  private static final long MAX_FUTURE_EVENT_MS = 5_000L;
  private static final long MAX_START_HORIZON_MS = 10_000L;

  private WorkoutSessionPhoneProjectionPolicy() {}

  /**
   * Starts require a fresh shared monotonic clock because both devices must honor one deadline.
   * Other durable controls can be applied immediately without a mapping; their captured wall time
   * remains on the command for route-boundary reconciliation.
   */
  public static WorkoutSessionProtocol.Command forPhone(
      WorkoutSessionProtocol.Command accepted,
      long phoneNowElapsedRealtimeMs,
      WorkoutSessionProtocol.ClockOffset offset,
      boolean offsetFresh) {
    return forPhone(accepted, phoneNowElapsedRealtimeMs, offset, offsetFresh, null);
  }

  public static WorkoutSessionProtocol.Command forPhone(
      WorkoutSessionProtocol.Command accepted,
      long phoneNowElapsedRealtimeMs,
      WorkoutSessionProtocol.ClockOffset offset,
      boolean offsetFresh,
      WorkoutSessionProtocol.WallClockOffset wallClockOffset) {
    if (accepted == null || phoneNowElapsedRealtimeMs < 0L) return null;
    boolean hasOffset = offsetFresh && offset != null && offset.isValid();
    boolean hasWallClockOffset = wallClockOffset != null && wallClockOffset.isValid();
    if (accepted.getAction() == WorkoutSessionProtocol.Action.START) {
      if (!hasOffset) return null;
      long issuedAt = offset.toPhoneElapsedRealtime(accepted.getIssuedAtElapsedRealtimeMs());
      long plannedStart =
          offset.toPhoneElapsedRealtime(accepted.getPlannedStartElapsedRealtimeMs());
      if (issuedAt < 0L
          || issuedAt > phoneNowElapsedRealtimeMs + MAX_FUTURE_EVENT_MS
          || plannedStart <= issuedAt
          || plannedStart > phoneNowElapsedRealtimeMs + MAX_START_HORIZON_MS) return null;
      return project(accepted, issuedAt, plannedStart, wallClockOffset);
    }

    if (accepted.getIssuedAtWallTimeMillis() > 0L && !hasWallClockOffset) return null;
    long issuedAt = phoneNowElapsedRealtimeMs;
    if (hasOffset) {
      long mapped = offset.toPhoneElapsedRealtime(accepted.getIssuedAtElapsedRealtimeMs());
      if (mapped >= 0L && mapped <= phoneNowElapsedRealtimeMs + MAX_FUTURE_EVENT_MS) {
        issuedAt = mapped;
      }
    }
    return project(accepted, issuedAt, 0L, wallClockOffset);
  }

  private static WorkoutSessionProtocol.Command project(
      WorkoutSessionProtocol.Command accepted,
      long issuedAt,
      long plannedStart,
      WorkoutSessionProtocol.WallClockOffset wallClockOffset) {
    long wallTime = accepted.getIssuedAtWallTimeMillis();
    if (wallTime > 0L && wallClockOffset != null && wallClockOffset.isValid()) {
      wallTime = wallClockOffset.toPhoneWallTime(wallTime);
    }
    return new WorkoutSessionProtocol.Command(
        accepted.getSessionId(),
        accepted.getCommandId(),
        accepted.getSequence(),
        accepted.getActor(),
        accepted.getAction(),
        issuedAt,
        plannedStart,
        wallTime);
  }
}
