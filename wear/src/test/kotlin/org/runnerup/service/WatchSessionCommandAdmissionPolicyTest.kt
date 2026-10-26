package org.runnerup.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.common.wear.WorkoutSessionProtocol.Action

class WatchSessionCommandAdmissionPolicyTest {
  @Test
  fun activeSessionControlsCanBeCommittedOffline() {
    listOf(Action.PAUSE, Action.RESUME, Action.LAP, Action.SKIP, Action.FINISH, Action.CANCEL)
      .forEach { action ->
        assertTrue(
          "${action.name} should be committed to the watch journal without a phone connection",
          WatchSessionCommandAdmissionPolicy.canCommitLocally(
            action,
            false,
            false,
          ),
        )
      }
  }

  @Test
  fun startRequiresConnectedPhoneAndSynchronizedClock() {
    assertFalse(
      WatchSessionCommandAdmissionPolicy.canCommitLocally(
        Action.START,
        false,
        true,
      ),
    )
    assertFalse(
      WatchSessionCommandAdmissionPolicy.canCommitLocally(
        Action.START,
        true,
        false,
      ),
    )
    assertTrue(
      WatchSessionCommandAdmissionPolicy.canCommitLocally(
        Action.START,
        true,
        true,
      ),
    )
  }

  @Test
  fun onlyNewSessionStartWaitsForOlderPendingProjection() {
    assertTrue(WatchSessionCommandAdmissionPolicy.mustWaitForPendingProjection(Action.START))
    listOf(Action.PAUSE, Action.RESUME, Action.LAP, Action.SKIP, Action.FINISH, Action.CANCEL)
      .forEach { action ->
        assertFalse(
          "${action.name} must not wait for an earlier journal event to be acknowledged",
          WatchSessionCommandAdmissionPolicy.mustWaitForPendingProjection(action),
        )
      }
  }

  @Test
  fun missingActionIsRejected() {
    assertFalse(
      WatchSessionCommandAdmissionPolicy.canCommitLocally(
        null,
        false,
        false,
      ),
    )
  }
}
