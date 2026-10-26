package org.runnerup.service;

import org.runnerup.common.wear.WorkoutSessionProtocol;

/** Admission rules for locally committing watch-authoritative session events. */
public final class WatchSessionCommandAdmissionPolicy {
  private WatchSessionCommandAdmissionPolicy() {}

  /**
   * An active session control is durable on the watch and may be queued for later phone delivery.
   * Starting still needs a connected phone and a fresh clock mapping for the shared start time.
   */
  public static boolean canCommitLocally(
      WorkoutSessionProtocol.Action action, boolean phoneConnected, boolean clockSynchronized) {
    if (action == null) return false;
    if (action == WorkoutSessionProtocol.Action.START) {
      return phoneConnected && clockSynchronized;
    }
    return true;
  }

  /** The old outbox head blocks a new session, but not more events in the current session. */
  public static boolean mustWaitForPendingProjection(WorkoutSessionProtocol.Action action) {
    return action == WorkoutSessionProtocol.Action.START;
  }
}
