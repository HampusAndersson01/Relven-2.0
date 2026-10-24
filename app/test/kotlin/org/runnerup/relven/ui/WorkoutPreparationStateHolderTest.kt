package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import org.runnerup.common.tracker.TrackerState

class WorkoutPreparationStateHolderTest {
  private fun newSessionId() = UUID.randomUUID().toString()
  @Test
  fun trackerConnectsDuringPreparationOnlyFromNonRecordingStates() {
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INIT))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INITIALIZING))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.INITIALIZED))
    assertTrue(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.CLEANUP))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.CONNECTED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.STARTED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.PAUSED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.STOPPED))
    assertFalse(WorkoutPreparationTrackerPolicy.shouldConnect(TrackerState.ERROR))
  }

  @Test
  fun startIsUnavailableUntilPreparationFinishes() {
    val state = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")

    assertEquals(WorkoutPreparationPhase.PREPARING, state.value.phase)
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 0))
  }

  @Test
  fun phoneOnlyFallbackCanStartOnlyOnceAfterPreparation() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertFalse(state.value.watchReady)
    assertTrue(state.requestStart(nowElapsedRealtimeMs = 10_000))
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 10_100))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
  }

  @Test
  fun failedStartCanReturnToReadyWithoutChangingSession() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")

    assertTrue(state.requestStart(nowElapsedRealtimeMs = 10_000))
    state.onStartFailed()

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertEquals(sessionId, state.value.sessionId)
    assertTrue(state.value.watchReady)
  }

  @Test
  fun startEntersThreeSecondMonotonicCountdownBeforeActive() {
    val state = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = false, status = "Fortsätt på mobilen")

    assertTrue(state.requestStart(nowElapsedRealtimeMs = 10_000))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
    assertEquals(13_000L, state.value.plannedStartElapsedRealtimeMs)
    assertEquals(3, state.countdownSeconds(10_000))
    assertEquals(1, state.countdownSeconds(12_100))
    assertFalse(state.isStartDue(12_999))
    assertTrue(state.isStartDue(13_000))
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 10_100))
    assertTrue(state.completeStart(nowElapsedRealtimeMs = 13_000))
    assertEquals(WorkoutPreparationPhase.ACTIVE, state.value.phase)
    assertFalse(state.requestStart(nowElapsedRealtimeMs = 14_000))
  }

  @Test
  fun countdownCanBeInterruptedWithoutStartingTracker() {
    val state = WorkoutPreparationStateHolder(newSessionId(), "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    assertTrue(state.requestStart(nowElapsedRealtimeMs = 1_000))

    val cancel = state.cancelCountdown()

    assertEquals(WorkoutPreparationPhase.READY, state.value.phase)
    assertFalse(state.isStartDue(4_000))
    assertEquals(org.runnerup.common.wear.WorkoutSessionProtocol.Action.CANCEL, cancel?.action)
    assertTrue(state.requestStart(nowElapsedRealtimeMs = 4_100))
    assertEquals(3L, state.lastCommand?.sequence)
  }

  @Test
  fun authoritativeWatchStartCanSynchronizeASequenceGapToThePhoneProjection() {
    val sessionId = newSessionId()
    val state = WorkoutPreparationStateHolder(sessionId, "Intervaller", "Uppvärmning")
    state.onWatchPreparationFinished(watchReady = true, status = "Klockan redo")
    val command =
        org.runnerup.common.wear.WorkoutSessionProtocol.Command(
            sessionId,
            UUID.randomUUID().toString(),
            5L,
            org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH,
            org.runnerup.common.wear.WorkoutSessionProtocol.Action.START,
            10_000L,
            13_000L)

    assertTrue(state.acceptAuthoritativeStart(command))
    assertEquals(WorkoutPreparationPhase.COUNTDOWN, state.value.phase)
    assertEquals(5L, state.lastCommand?.sequence)
    assertEquals(13_000L, state.value.plannedStartElapsedRealtimeMs)
  }
}
