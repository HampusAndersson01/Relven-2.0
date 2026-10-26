package org.runnerup.relven.ui

import org.runnerup.relven.exercise.ExerciseCatalog

internal data class PlanExercisePresentation(
    val name: String,
    val instructions: String,
    val instructionSteps: List<String>)

internal fun planExercisePresentation(
    exerciseId: String,
    snapshotName: String,
    snapshotInstructions: String,
    catalog: ExerciseCatalog?
): PlanExercisePresentation {
  val exercises = catalog?.exercises.orEmpty()
  val localizedExercise =
      exercises.firstOrNull { it.exerciseId == exerciseId }
          ?: exercises
              .filter {
                it.nameEnglish == snapshotName &&
                    it.instructionsEnglish.joinToString("\n") == snapshotInstructions
              }
              .singleOrNull()
  val instructionSteps =
      localizedExercise?.instructions
          ?: snapshotInstructions.split('\n').map(String::trim).filter(String::isNotEmpty)
  return PlanExercisePresentation(
      name = localizedExercise?.name ?: snapshotName,
      instructions = instructionSteps.joinToString("\n"),
      instructionSteps = instructionSteps)
}
