package org.runnerup.tracker.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.runnerup.tracker.component.TrackerComponent.ResultCode;

public class WearConnectionInitializationPolicyTest {
  @Test
  public void transientInitialFailureKeepsOptionalWearComponentEligibleAfterReconnect() {
    WearConnectionInitializationPolicy policy = new WearConnectionInitializationPolicy();

    assertEquals(ResultCode.RESULT_OK, policy.resolve());
    assertNull("a later reconnect must not complete initialization twice", policy.resolve());
  }
}
