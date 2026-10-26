package org.runnerup.tracker.component;

/** Completes optional Wear initialization once while allowing later transport recovery. */
final class WearConnectionInitializationPolicy {
  private boolean resolved;

  TrackerComponent.ResultCode resolve() {
    if (resolved) return null;
    resolved = true;
    return TrackerComponent.ResultCode.RESULT_OK;
  }
}
