package org.runnerup.tracker.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.runnerup.tracker.component.TrackerComponent.ResultCode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WearConnectionInitializationPolicyTest {
  @Test
  public void transientInitialFailureKeepsOptionalWearComponentEligibleAfterReconnect() {
    WearConnectionInitializationPolicy policy = new WearConnectionInitializationPolicy();

    assertEquals(ResultCode.RESULT_OK, policy.resolve());
    assertNull("a later reconnect must not complete initialization twice", policy.resolve());
  }

  @Test
  public void initialFailureThenReconnectKeepsWearEligibleForWorkoutDispatch() {
    Context context = RuntimeEnvironment.getApplication();
    TrackerComponentCollection collection = new TrackerComponentCollection();
    ReconnectingWearComponent wear = new ReconnectingWearComponent();
    collection.addComponent(wear);

    assertEquals(
        ResultCode.RESULT_PENDING,
        collection.onInit((component, result) -> {}, context));
    wear.reportInitialConnectionFailure();
    Shadows.shadowOf(Looper.getMainLooper()).idle();

    assertEquals(ResultCode.RESULT_OK, collection.getResultCode(ReconnectingWearComponent.NAME));
    wear.reportSuccessfulReconnect();
    assertEquals(
        ResultCode.RESULT_OK,
        collection.onConnecting((component, result) -> {}, context));

    collection.onConnected();
    collection.onStart();
    assertTrue("reconnected Wear component should receive workout lifecycle callbacks", wear.started);
  }

  private static final class ReconnectingWearComponent extends DefaultTrackerComponent {
    static final String NAME = "TEST_WEAR";

    private final WearConnectionInitializationPolicy initializationPolicy =
        new WearConnectionInitializationPolicy();
    private Callback initializationCallback;
    private boolean started;

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public ResultCode onInit(Callback callback, Context context) {
      initializationCallback = callback;
      return ResultCode.RESULT_PENDING;
    }

    void reportInitialConnectionFailure() {
      initializationCallback.run(this, initializationPolicy.resolve());
    }

    void reportSuccessfulReconnect() {
      ResultCode result = initializationPolicy.resolve();
      if (result != null) initializationCallback.run(this, result);
    }

    @Override
    public void onStart() {
      started = true;
    }
  }
}
