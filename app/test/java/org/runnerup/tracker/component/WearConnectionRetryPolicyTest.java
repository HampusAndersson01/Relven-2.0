package org.runnerup.tracker.component;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WearConnectionRetryPolicyTest {
  @Test
  public void retryDelayUsesBoundedExponentialBackoff() {
    assertEquals(1_000L, WearConnectionRetryPolicy.delayForAttempt(0));
    assertEquals(2_000L, WearConnectionRetryPolicy.delayForAttempt(1));
    assertEquals(4_000L, WearConnectionRetryPolicy.delayForAttempt(2));
    assertEquals(8_000L, WearConnectionRetryPolicy.delayForAttempt(3));
    assertEquals(16_000L, WearConnectionRetryPolicy.delayForAttempt(4));
    assertEquals(30_000L, WearConnectionRetryPolicy.delayForAttempt(5));
    assertEquals(30_000L, WearConnectionRetryPolicy.delayForAttempt(20));
  }
}
