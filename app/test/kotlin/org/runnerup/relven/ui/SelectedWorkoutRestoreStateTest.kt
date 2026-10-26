package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.runnerup.common.tracker.TrackerState

class SelectedWorkoutRestoreStateTest {
  @Test
  fun pendingRestoreIsNotReportedAsFailure() {
    assertEquals(
        SelectedWorkoutRestoreState.RESTORING,
        selectedWorkoutRestoreState(restorePending = true, available = false))
  }

  @Test
  fun completedRestoreDistinguishesSuccessFromFailure() {
    assertEquals(
        SelectedWorkoutRestoreState.AVAILABLE,
        selectedWorkoutRestoreState(restorePending = false, available = true))
    assertEquals(
        SelectedWorkoutRestoreState.FAILED,
        selectedWorkoutRestoreState(restorePending = false, available = false))
  }

  @Test
  fun onlyFatalTrackerErrorFailsAPendingRestore() {
    assertEquals(
        true,
        WorkoutPreparationTrackerPolicy.shouldFailSelectedWorkoutRestore(
            restorePending = true, state = TrackerState.ERROR))
    assertEquals(
        false,
        WorkoutPreparationTrackerPolicy.shouldFailSelectedWorkoutRestore(
            restorePending = true, state = TrackerState.INITIALIZING))
    assertEquals(
        false,
        WorkoutPreparationTrackerPolicy.shouldFailSelectedWorkoutRestore(
            restorePending = false, state = TrackerState.ERROR))
  }
}
