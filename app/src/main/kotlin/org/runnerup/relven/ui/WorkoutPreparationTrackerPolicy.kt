package org.runnerup.relven.ui

import org.runnerup.common.tracker.TrackerState

internal enum class SelectedWorkoutRestoreState {
  RESTORING,
  AVAILABLE,
  EXISTING_SESSION,
  FAILED
}

internal fun selectedWorkoutRestoreState(
    restorePending: Boolean,
    available: Boolean,
    trackerState: TrackerState? = null
): SelectedWorkoutRestoreState =
    when {
      trackerState != null && WorkoutPreparationTrackerPolicy.hasExistingWorkout(trackerState) ->
          SelectedWorkoutRestoreState.EXISTING_SESSION
      restorePending -> SelectedWorkoutRestoreState.RESTORING
      available -> SelectedWorkoutRestoreState.AVAILABLE
      else -> SelectedWorkoutRestoreState.FAILED
    }

/** Connects RunnerUp's existing engine during preparation without starting workout recording. */
internal object WorkoutPreparationTrackerPolicy {
  fun hasExistingWorkout(state: TrackerState): Boolean =
      state == TrackerState.STARTED || state == TrackerState.PAUSED || state == TrackerState.STOPPED

  fun shouldAttachWearSession(workoutAvailable: Boolean, state: TrackerState): Boolean =
      workoutAvailable && state == TrackerState.CONNECTED

  fun shouldConnect(state: TrackerState): Boolean =
      state == TrackerState.INIT ||
          state == TrackerState.INITIALIZING ||
          state == TrackerState.INITIALIZED

  fun shouldRetryAfterCleanup(state: TrackerState): Boolean = state == TrackerState.INIT

  fun shouldRestoreSelectedWorkout(restorePending: Boolean, state: TrackerState): Boolean =
      restorePending && state == TrackerState.CONNECTED

  fun shouldFailSelectedWorkoutRestore(restorePending: Boolean, state: TrackerState): Boolean =
      restorePending && state == TrackerState.ERROR

  fun shouldInitiateConnection(connectionAttemptPending: Boolean, state: TrackerState): Boolean =
      !connectionAttemptPending && shouldConnect(state)

  fun shouldResetAfterCancellation(
      connectionInitiatedByPreparation: Boolean, state: TrackerState): Boolean =
      connectionInitiatedByPreparation &&
          (state == TrackerState.INITIALIZING ||
              state == TrackerState.INITIALIZED ||
              state == TrackerState.CONNECTING ||
              state == TrackerState.CONNECTED ||
              state == TrackerState.ERROR)

  fun shouldResetAfterRejectedStart(
      cancelWasRequested: Boolean,
      connectionInitiatedByPreparation: Boolean,
      state: TrackerState): Boolean =
      cancelWasRequested && shouldResetAfterCancellation(connectionInitiatedByPreparation, state)
}
