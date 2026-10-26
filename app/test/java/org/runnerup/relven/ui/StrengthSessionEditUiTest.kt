package org.runnerup.relven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.relven.exercise.ExerciseCatalog
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StrengthSessionEditUiTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun activeSessionShowsLocalizedCatalogNameAndSeparateInstructionSteps() {
    val exerciseId = "3b0e6f4b-9252-4e8c-96e5-7df5e0bb18d0"
    val catalog =
        ExerciseCatalog.parse(
            """{"format":"relven.exercise-library","schemaVersion":1,"libraryRevision":2,"exercises":[{"exerciseId":"$exerciseId","revision":1,"name":{"en":"Bodyweight squat","sv":"Knäböj"},"category":"lower_body","movementPattern":"squat","muscleGroups":["quadriceps"],"equipment":[],"instructions":{"en":["Stand with feet hip-width apart.","Keep your chest lifted."],"sv":["Stå med fötterna höftbrett.","Håll bröstet lyft."]}}]}""",
            "sv-SE")
    val workout =
        RelvenStrengthWorkoutParser.Workout(
            "workout",
            "Strength",
            "strength",
            listOf(
                RelvenStrengthWorkoutParser.Exercise(
                    "squat-step",
                    exerciseId,
                    2,
                    1,
                    "Bodyweight squat",
                    "Stand with feet hip-width apart.\nKeep your chest lifted.",
                    1,
                    1,
                    20,
                    0,
                    null)),
            "{}")

    composeRule.setContent {
      StrengthSessionScreen(
          session = StrengthSession.start("plan", "entry", workout),
          exerciseCatalog = catalog,
          completedAtMillis = null,
          saveError = false,
          isSavingProgress = false,
          isSavingCompletion = false,
          onEditResult = {},
          onTick = {},
          onLogRepetitions = {},
          onSkipPendingResult = {},
          onSkipExercise = {},
          onSkipRest = {},
          onBegin = {},
          onPause = {},
          onResume = {},
          onFinish = {},
          onClose = {})
    }

    composeRule.onNodeWithText("Knäböj").assertExists()
    composeRule.onNodeWithText("Stå med fötterna höftbrett.").assertExists()
    composeRule.onNodeWithText("Håll bröstet lyft.").assertExists()
    composeRule.onAllNodesWithText("Bodyweight squat").assertCountEquals(0)
  }

  @Test
  fun savedSetCanBeCorrectedWithoutLosingItsOptionalDetails() {
    val workout =
        RelvenStrengthWorkoutParser.Workout(
            "workout",
            "Strength",
            "strength",
            listOf(
                RelvenStrengthWorkoutParser.Exercise(
                    "push-up-step",
                    "push-up",
                    1,
                    1,
                    "Push-up",
                    "Controlled repetitions",
                    1,
                    1,
                    20,
                    0,
                    null)),
            "{}")
    val finished =
        StrengthSession.start("plan", "entry", workout)
            .completeResult(
                StrengthSession.RepetitionResultInput(repetitions = 10, rpe = 7.0, painRating = 2, notes = "Steady"))
    var edited: StrengthSession.SetResult? = null

    composeRule.setContent {
      StrengthSessionScreen(
          session = finished,
          completedAtMillis = 100,
          saveError = false,
          isSavingProgress = false,
          isSavingCompletion = false,
          onEditResult = { edited = it },
          onTick = {},
          onLogRepetitions = {},
          onSkipPendingResult = {},
          onSkipExercise = {},
          onSkipRest = {},
          onBegin = {},
          onPause = {},
          onResume = {},
          onFinish = {},
          onClose = {})
    }

    composeRule.onNodeWithText("Edit").performScrollTo().performClick()
    composeRule.onNodeWithText("15").performScrollTo().performClick()
    composeRule.onNodeWithText("Save change").performScrollTo().performClick()

    composeRule.runOnIdle {
      val result = requireNotNull(edited)
      assertEquals(15, result.repetitions)
      assertEquals(7.0, result.rpe!!, 0.0)
      assertEquals(2, result.painRating)
      assertEquals("Steady", result.notes)
    }
  }

  @Test
  fun finishingIsDisabledWhileASetCorrectionIsBeingSaved() {
    val workout =
        RelvenStrengthWorkoutParser.Workout(
            "workout",
            "Strength",
            "strength",
            listOf(
                RelvenStrengthWorkoutParser.Exercise(
                    "push-up-step", "push-up", 1, 1, "Push-up", "Controlled repetitions", 1, 1, 20, 0, null)),
            "{}")
    val finished =
        StrengthSession.start("plan", "entry", workout)
            .completeResult(StrengthSession.RepetitionResultInput(repetitions = 10))

    composeRule.setContent {
      StrengthSessionScreen(
          session = finished,
          completedAtMillis = null,
          saveError = false,
          isSavingProgress = true,
          isSavingCompletion = false,
          onEditResult = {},
          onTick = {},
          onLogRepetitions = {},
          onSkipPendingResult = {},
          onSkipExercise = {},
          onSkipRest = {},
          onBegin = {},
          onPause = {},
          onResume = {},
          onFinish = {},
          onClose = {})
    }

    composeRule.onNodeWithText("Save completed session").assertIsNotEnabled()
  }
}
