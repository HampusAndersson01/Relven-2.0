package org.runnerup.tracker.component;

/** Backoff for restoring the phone-to-Wear Data Layer connection after transient loss. */
final class WearConnectionRetryPolicy {
  private static final long INITIAL_DELAY_MS = 1_000L;
  private static final long MAX_DELAY_MS = 30_000L;

  private WearConnectionRetryPolicy() {}

  static long delayForAttempt(int attempt) {
    if (attempt <= 0) return INITIAL_DELAY_MS;
    if (attempt >= 5) return MAX_DELAY_MS;
    return Math.min(MAX_DELAY_MS, INITIAL_DELAY_MS << attempt);
  }
}
