package org.runnerup.relven.strength

import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import org.runnerup.workout.RelvenStrengthWorkoutParser

/** Portable public export for a completed strength/prehab execution and its immutable prescription. */
internal object StrengthSessionExportCodec {
  private const val FORMAT = "relven.strength-session"
  private const val VERSION = 1
  private const val MAX_EXPORT_BYTES = 16_777_216

  fun encode(completion: CompletedStrengthSession): String {
    val state = completion.state
    require(state.isComplete && state.pendingResults.isEmpty()) { "Only completed sessions can be exported" }
    require(completion.finishedAtMillis > 0) { "Invalid completion timestamp" }
    val sets = JSONArray()
    state.results.sortedWith(compareBy(StrengthSession.SetResult::exerciseIndex, StrengthSession.SetResult::setNumber))
        .forEach { result ->
          val item =
              JSONObject()
                  .put("exerciseIndex", result.exerciseIndex)
                  .put("exerciseStepId", result.exerciseStepId)
                  .put("exerciseName", result.exerciseName)
                  .put("setNumber", result.setNumber)
                  .put("status", result.status.name)
          result.repetitions?.let { item.put("repetitions", it) }
          result.durationSeconds?.let { item.put("durationSeconds", it) }
          result.restDurationSeconds?.let { item.put("restDurationSeconds", it) }
          result.leftRepetitions?.let { item.put("leftRepetitions", it) }
          result.rightRepetitions?.let { item.put("rightRepetitions", it) }
          result.startedAtMillis?.let { item.put("startedAtMillis", it) }
          result.completedAtMillis?.let { item.put("completedAtMillis", it) }
          result.rpe?.let { item.put("rpe", it) }
          result.painRating?.let { item.put("painRating", it) }
          result.notes?.let { item.put("notes", it) }
          result.exerciseCategory?.let { item.put("exerciseCategory", it) }
          sets.put(item)
        }
    val completionState = if (state.results.all { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED }) {
      "complete"
    } else {
      "partial"
    }
    val performance =
        JSONObject()
            .put("sessionId", state.executionId)
            .put("planId", state.planId)
            .put("scheduledEntryId", state.scheduledEntryId)
            .put("sessionStatus", "COMPLETED")
            .put("completion", completionState)
            .put("finishedAtMillis", completion.finishedAtMillis)
            .put("sets", sets)
    return JSONObject()
        .put("format", FORMAT)
        .put("schemaVersion", VERSION)
        .put("prescription", JSONObject(state.workout.sourceJson()))
        .put("performance", performance)
        .toString()
        .also { require(it.toByteArray(StandardCharsets.UTF_8).size <= MAX_EXPORT_BYTES) { "Session export is too large" } }
  }

  fun decode(json: String): CompletedStrengthSession {
    require(json.toByteArray(StandardCharsets.UTF_8).size <= MAX_EXPORT_BYTES) { "Session import is too large" }
    val root = JSONObject(json)
    requireKeys(root, setOf("format", "schemaVersion", "prescription", "performance"))
    require(root.getString("format") == FORMAT) { "Unsupported workout export format" }
    require(root.getInt("schemaVersion") == VERSION) { "Unsupported workout export version" }

    val workout = RelvenStrengthWorkoutParser.parse(root.getJSONObject("prescription").toString())
    val performance = root.getJSONObject("performance")
    requireKeys(
        performance,
        setOf("sessionId", "planId", "scheduledEntryId", "sessionStatus", "completion", "finishedAtMillis", "sets"))
    val sessionId = requiredText(performance, "sessionId", 160)
    val planId = requiredText(performance, "planId", 160)
    val scheduledEntryId = requiredText(performance, "scheduledEntryId", 160)
    require(performance.getString("sessionStatus") == "COMPLETED") { "Only completed sessions can be imported" }
    val finishedAtMillis = performance.getLong("finishedAtMillis")
    require(finishedAtMillis > 0) { "Invalid completion timestamp" }

    val setJson = performance.getJSONArray("sets")
    require(setJson.length() <= 100_000) { "Too many imported set results" }
    val results = ArrayList<StrengthSession.SetResult>(setJson.length())
    repeat(setJson.length()) { index ->
      val item = setJson.getJSONObject(index)
      requireKeys(
          item,
          setOf(
              "exerciseIndex", "exerciseStepId", "exerciseName", "setNumber", "status", "repetitions",
              "durationSeconds", "restDurationSeconds", "leftRepetitions", "rightRepetitions", "startedAtMillis",
              "completedAtMillis", "rpe", "painRating", "notes", "exerciseCategory"))
      val exerciseIndex = item.getInt("exerciseIndex")
      val exercise = workout.exercises().getOrNull(exerciseIndex)
          ?: throw IllegalArgumentException("Invalid result exercise position")
      val stepId = item.getString("exerciseStepId")
      val exerciseName = item.getString("exerciseName")
      require(stepId == exercise.stepId() && exerciseName == exercise.name()) {
        "Set result does not match its prescription snapshot"
      }
      val result =
          StrengthSession.SetResult(
              exerciseIndex = exerciseIndex,
              exerciseStepId = stepId,
              exerciseName = exerciseName,
              setNumber = item.getInt("setNumber"),
              repetitions = item.optionalInt("repetitions"),
              durationSeconds = item.optionalInt("durationSeconds"),
              restDurationSeconds = item.optionalInt("restDurationSeconds"),
              leftRepetitions = item.optionalInt("leftRepetitions"),
              rightRepetitions = item.optionalInt("rightRepetitions"),
              startedAtMillis = item.optionalLong("startedAtMillis"),
              completedAtMillis = item.optionalLong("completedAtMillis"),
              rpe = item.optionalDouble("rpe"),
              painRating = item.optionalInt("painRating"),
              notes = item.optionalText("notes"),
              status = StrengthSession.SetResult.ResultStatus.valueOf(item.getString("status")),
              exerciseCategory = item.optionalText("exerciseCategory"))
      require(result.setNumber in 1..exercise.sets()) { "Invalid set number" }
      require(result.startedAtMillis == null || result.startedAtMillis <= finishedAtMillis) {
        "Set start occurs after session completion"
      }
      require(result.completedAtMillis == null || result.completedAtMillis <= finishedAtMillis) {
        "Set completion occurs after session completion"
      }
      results += result
    }
    require(results.map { it.exerciseIndex to it.setNumber }.toSet().size == results.size) {
      "Duplicate imported set result"
    }
    val expectedSetCount = workout.exercises().sumOf { it.sets() }
    require(results.size == expectedSetCount) { "Completed session is missing a set status" }
    val isComplete = results.all { it.status == StrengthSession.SetResult.ResultStatus.COMPLETED }
    require(performance.getString("completion") == if (isComplete) "complete" else "partial") {
      "Session completion marker does not match its set results"
    }

    val initial = StrengthSession.start(planId, scheduledEntryId, workout)
    val imported =
        initial.copy(
            executionId = sessionId,
            phase = StrengthSession.Phase.COMPLETE,
            secondsRemaining = 0,
            results = results.sortedWith(compareBy(StrengthSession.SetResult::exerciseIndex, StrengthSession.SetResult::setNumber)),
            pendingResults = emptyList(),
            timerDeadlineElapsedRealtimeMillis = null,
            timerAnchorElapsedRealtimeMillis = null,
            timerClockObservedElapsedRealtimeMillis = null)
    // Reuse the private session validator before the imported record reaches durable storage.
    val validated = StrengthSessionCodec.decode(StrengthSessionCodec.encode(imported, finishedAtMillis)).first
    return CompletedStrengthSession(validated, finishedAtMillis)
  }

  private fun requiredText(json: JSONObject, key: String, maxLength: Int): String {
    val value = json.getString(key)
    require(value.isNotBlank() && value.length <= maxLength) { "Invalid $key" }
    return value
  }

  private fun requireKeys(json: JSONObject, allowed: Set<String>) {
    require(json.keys().asSequence().all(allowed::contains)) { "Unexpected workout export field" }
  }

  private fun JSONObject.optionalInt(key: String): Int? = if (has(key)) getInt(key) else null
  private fun JSONObject.optionalLong(key: String): Long? = if (has(key)) getLong(key) else null
  private fun JSONObject.optionalDouble(key: String): Double? = if (has(key)) getDouble(key) else null
  private fun JSONObject.optionalText(key: String): String? = if (has(key)) getString(key) else null
}
