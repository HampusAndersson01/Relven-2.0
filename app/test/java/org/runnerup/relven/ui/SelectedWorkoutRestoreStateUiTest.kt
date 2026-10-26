package org.runnerup.relven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SelectedWorkoutRestoreStateUiTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun pendingRestoreShowsProgressInsteadOfFailure() {
    composeRule.setContent {
      WorkoutPreparationScreen(
          state = preparationUiState(),
          trackerReady = false,
          selectedWorkoutRestoreState = SelectedWorkoutRestoreState.RESTORING,
          onStart = {},
          onCancel = {})
    }

    composeRule.onNodeWithText("Återställer valt pass…").assertExists()
    composeRule.onAllNodesWithText(
        "Passet kunde inte återställas. Avbryt och välj passet igen.").assertCountEquals(0)
    composeRule.onNodeWithText("Förbereder träningsmotorn").assertIsNotEnabled()
  }

  @Test
  fun failedRestoreExplainsFailureAndKeepsStartDisabled() {
    composeRule.setContent {
      WorkoutPreparationScreen(
          state = preparationUiState(),
          trackerReady = false,
          selectedWorkoutRestoreState = SelectedWorkoutRestoreState.FAILED,
          onStart = {},
          onCancel = {})
    }

    composeRule.onNodeWithText("Passet kunde inte återställas").assertExists()
    composeRule.onNodeWithText(
        "Passet kunde inte återställas. Avbryt och välj passet igen.").assertExists()
    composeRule.onNodeWithText("Passet kunde inte återställas").assertIsNotEnabled()
  }

  @Test
  fun existingWorkoutOffersSafeNavigationWithoutEnablingPlannedStart() {
    composeRule.setContent {
      WorkoutPreparationScreen(
          state = preparationUiState(),
          trackerReady = false,
          selectedWorkoutRestoreState = SelectedWorkoutRestoreState.EXISTING_SESSION,
          onStart = {},
          onCancel = {},
          onOpenExistingWorkout = {})
    }

    composeRule.onNodeWithText("Pågående pass hittades").assertExists()
    composeRule.onNodeWithText("Ett annat pass behöver avslutas först.").assertExists()
    composeRule.onNodeWithText("Öppna pågående pass").assertHasClickAction()
    composeRule.onNodeWithText("Avsluta pågående pass först").assertIsNotEnabled()
  }

  @Test
  fun runningWorkoutWaitsForGpsBeforeEnablingCountdown() {
    composeRule.setContent {
      WorkoutPreparationScreen(
          state = preparationUiState().copy(phase = WorkoutPreparationPhase.READY),
          trackerReady = true,
          gpsReadiness = GpsPreparationUiState(GpsPreparationStatus.SEARCHING),
          onStart = {},
          onCancel = {})
    }

    composeRule.onNodeWithText("Väntar på GPS").assertIsNotEnabled()
    composeRule.onNodeWithText("Söker GPS-signal · passet startar när GPS är redo").assertExists()
  }

  @Test
  fun runningWorkoutEnablesCountdownAfterGpsIsReady() {
    composeRule.setContent {
      WorkoutPreparationScreen(
          state = preparationUiState().copy(phase = WorkoutPreparationPhase.READY),
          trackerReady = true,
          gpsReadiness = GpsPreparationUiState(GpsPreparationStatus.READY, accuracyMeters = 8f),
          onStart = {},
          onCancel = {})
    }

    composeRule.onNodeWithText("Starta pass · 3 sek nedräkning").assertHasClickAction().assertIsEnabled()
  }

  private fun preparationUiState() =
      WorkoutPreparationUiState(
          sessionId = "session",
          workoutName = "Easy run · 30 min",
          upcomingStep = "Lugn löpning",
          phase = WorkoutPreparationPhase.PREPARING,
          watchReady = false,
          status = "Ansluter till träningsmotorn")
}
