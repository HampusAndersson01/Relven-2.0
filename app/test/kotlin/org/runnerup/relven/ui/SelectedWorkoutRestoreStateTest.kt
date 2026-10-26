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

  @Test
  fun anExistingTrackerSessionBlocksPlannedRestoreUntilItIsResolved() {
    listOf(TrackerState.STARTED, TrackerState.PAUSED, TrackerState.STOPPED).forEach { state ->
      assertEquals(
          "Tracker state $state must not leave plan restoration spinning",
          SelectedWorkoutRestoreState.EXISTING_SESSION,
          selectedWorkoutRestoreState(
              restorePending = true,
              available = false,
              trackerState = state))
    }
  }

  @Test
  fun plannedRestoreResumesAfterExistingSessionReturnsToConnectionStates() {
    listOf(
            TrackerState.INIT,
            TrackerState.INITIALIZING,
            TrackerState.INITIALIZED,
            TrackerState.CONNECTING,
            TrackerState.CLEANUP)
        .forEach { state ->
          assertEquals(
              "Tracker state $state should permit restoration to continue",
              SelectedWorkoutRestoreState.RESTORING,
              selectedWorkoutRestoreState(
                  restorePending = true,
                  available = false,
                  trackerState = state))
        }
  }

  @Test
  fun selectedWorkoutBecomesAvailableAgainAfterExistingSessionEnds() {
    assertEquals(
        SelectedWorkoutRestoreState.AVAILABLE,
        selectedWorkoutRestoreState(
            restorePending = false,
            available = true,
            trackerState = TrackerState.CONNECTED))
  }

  @Test
  fun newWearSessionIsAttachedOnlyAfterTheSelectedWorkoutIsSafeToStart() {
    assertEquals(
        false,
        WorkoutPreparationTrackerPolicy.shouldAttachWearSession(
            workoutAvailable = true, TrackerState.STARTED))
    assertEquals(
        false,
        WorkoutPreparationTrackerPolicy.shouldAttachWearSession(
            workoutAvailable = true, TrackerState.PAUSED))
    assertEquals(
        false,
        WorkoutPreparationTrackerPolicy.shouldAttachWearSession(
            workoutAvailable = true, TrackerState.STOPPED))
    assertEquals(
        false,
        WorkoutPreparationTrackerPolicy.shouldAttachWearSession(
            workoutAvailable = false, TrackerState.CONNECTED))
    assertEquals(
        true,
        WorkoutPreparationTrackerPolicy.shouldAttachWearSession(
            workoutAvailable = true, TrackerState.CONNECTED))
  }
}
