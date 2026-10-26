package org.runnerup.relven.telemetry

import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.wear.WorkoutTelemetryProtocol

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkoutTelemetryInboxStoreTest {
  private val database = SQLiteDatabase.create(null)
  private val store = WorkoutTelemetryInboxStore(database).also { it.ensureSchema() }

  @After
  fun closeDatabase() = database.close()

  @Test
  fun acceptsSessionMappingReplayAndRejectsConflictingActivity() {
    assertEquals(
        WorkoutTelemetryInboxStore.AssociationResult.ASSOCIATED,
        store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START))
    assertEquals(
        WorkoutTelemetryInboxStore.AssociationResult.DUPLICATE,
        store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START))
    assertEquals(
        WorkoutTelemetryInboxStore.AssociationResult.CONFLICT,
        store.associateActivity(SESSION, ACTIVITY_ID + 1, SESSION_START))
  }

  @Test
  fun rejectsInvalidActivityAssociation() {
    assertEquals(
        WorkoutTelemetryInboxStore.AssociationResult.INVALID,
        store.associateActivity("", ACTIVITY_ID, SESSION_START))
    assertEquals(
        WorkoutTelemetryInboxStore.AssociationResult.INVALID,
        store.associateActivity(SESSION, 0, SESSION_START))
    assertEquals(
        WorkoutTelemetryInboxStore.AssociationResult.INVALID,
        store.associateActivity(SESSION, ACTIVITY_ID, 0))
  }

  @Test
  fun commitsContiguousBatchAndAcknowledgesIdenticalReplay() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    val batch = batch(sample(1), sample(2))

    val committed = store.commitBatch(batch, NOW)
    val replayed = store.commitBatch(batch, NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.COMMITTED, committed.status)
    assertEquals(2L, committed.committedThroughSequence)
    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.DUPLICATE, replayed.status)
    assertEquals(2L, replayed.committedThroughSequence)
    assertEquals(2, store.sampleCount(SESSION))
  }

  @Test
  fun gapDoesNotAdvanceCursorOrPersistAnySamples() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)

    val result = store.commitBatch(batch(sample(2)), NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.GAP, result.status)
    assertEquals(0L, result.committedThroughSequence)
    assertEquals(0, store.sampleCount(SESSION))
  }

  @Test
  fun acceptsIdenticalPartialOverlapAndAppendsOnlyNewTail() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    val first = sample(1)
    val second = sample(2)
    store.commitBatch(batch(first, second), NOW)

    val result = store.commitBatch(batch(second, sample(3)), NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.COMMITTED, result.status)
    assertEquals(3L, result.committedThroughSequence)
    assertEquals(3, store.sampleCount(SESSION))
  }

  @Test
  fun storageFailureRollsBackEntireBatch() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    database.execSQL(
        "CREATE TRIGGER reject_telemetry BEFORE INSERT ON relven_wear_telemetry_sample " +
            "BEGIN SELECT RAISE(ABORT, 'test failure'); END")

    val result = store.commitBatch(batch(sample(1)), NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.STORAGE_ERROR, result.status)
    assertEquals(0L, result.committedThroughSequence)
    assertEquals(0, store.sampleCount(SESSION))
  }

  @Test
  fun inboxSurvivesDatabaseReopenAndReplayedBatchIsIdempotent() {
    val file = File.createTempFile("wear-telemetry", ".db")
    file.delete()
    val firstDatabase = SQLiteDatabase.openOrCreateDatabase(file, null)
    try {
      val firstStore = WorkoutTelemetryInboxStore(firstDatabase).also { it.ensureSchema() }
      firstStore.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
      assertEquals(
          WorkoutTelemetryInboxStore.CommitStatus.COMMITTED,
          firstStore.commitBatch(batch(sample(1)), NOW).status)
    } finally {
      firstDatabase.close()
    }
    val reopened = SQLiteDatabase.openOrCreateDatabase(file, null)
    try {
      val reopenedStore = WorkoutTelemetryInboxStore(reopened).also { it.ensureSchema() }
      val replay = reopenedStore.commitBatch(batch(sample(1)), NOW)
      assertEquals(WorkoutTelemetryInboxStore.CommitStatus.DUPLICATE, replay.status)
      assertEquals(1, reopenedStore.sampleCount(SESSION))
    } finally {
      reopened.close()
      file.delete()
    }
  }

  @Test
  fun conflictingDuplicateDoesNotChangeCommittedSamples() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    store.commitBatch(batch(sample(1)), NOW)
    val conflict = sample(1, latitude = 59.34)

    val result = store.commitBatch(batch(conflict), NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.CONFLICT, result.status)
    assertEquals(1L, result.committedThroughSequence)
    assertEquals(1, store.sampleCount(SESSION))
  }

  @Test
  fun missingStoredSequenceCannotBeAcknowledgedOrReplayedAsNew() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    store.commitBatch(batch(sample(1)), NOW)
    database.delete("relven_wear_telemetry_sample", "session_id = ? AND sequence = ?", arrayOf(SESSION, "1"))

    val replay = store.commitBatch(batch(sample(1)), NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.CONFLICT, replay.status)
    assertEquals(1L, replay.committedThroughSequence)
    assertEquals(0, store.sampleCount(SESSION))
    assertFalse(replay.shouldAcknowledge)
  }

  @Test
  fun onlyCommittedAndIdenticalDuplicateBatchesCanBeAcknowledged() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    val committed = store.commitBatch(batch(sample(1)), NOW)
    val duplicate = store.commitBatch(batch(sample(1)), NOW)
    val conflicting = store.commitBatch(batch(sample(1, latitude = 59.34)), NOW)
    val unassociatedSample = sample(1, sessionId = "not-associated")
    val unknownSession =
        store.commitBatch(
            WorkoutTelemetryProtocol.Batch(unassociatedSample.sessionId, listOf(unassociatedSample)),
            NOW)

    assertTrue(committed.shouldAcknowledge)
    assertTrue(duplicate.shouldAcknowledge)
    assertFalse(conflicting.shouldAcknowledge)
    assertFalse(unknownSession.shouldAcknowledge)
  }

  @Test
  fun rejectsUnknownSessionAndSamplesOutsideActivityWindow() {
    val unknown = store.commitBatch(batch(sample(1)), NOW)
    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.UNKNOWN_SESSION, unknown.status)
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)

    val beforeStart = store.commitBatch(batch(sample(1, wallTimeMillis = SESSION_START - 121_000)), NOW)
    val future = store.commitBatch(batch(sample(1, wallTimeMillis = NOW + 60_001)), NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.INVALID_TIME, beforeStart.status)
    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.INVALID_TIME, future.status)
    assertEquals(0, store.sampleCount(SESSION))
  }

  @Test
  fun matchingDuplicateRowsRemainByteIdentical() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    val first = sample(1)
    store.commitBatch(batch(first), NOW)

    assertTrue(store.containsIdenticalSample(first))
    assertFalse(store.containsIdenticalSample(sample(1, latitude = 59.34)))
  }

  @Test
  fun duplicateSamplesRemainIdempotentWhenRetryClockAnchorChanges() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    val skewedSample = sample(
        1,
        wallTimeMillis = SESSION_START + 601_000,
        elapsedRealtimeNanos = 1_000_000_000L)
    val firstAnchor = clockAnchor(offsetMillis = 600_000, syncElapsedMillis = 60_000)
    val retryAnchor = clockAnchor(offsetMillis = 3_600_000, syncElapsedMillis = 61_000)

    assertEquals(
        WorkoutTelemetryInboxStore.CommitStatus.COMMITTED,
        store.commitBatch(batch(skewedSample, firstAnchor), NOW).status)
    assertEquals(
        WorkoutTelemetryInboxStore.CommitStatus.DUPLICATE,
        store.commitBatch(batch(skewedSample, retryAnchor), NOW).status)
  }

  @Test
  fun v1InboxPayloadRemainsIdempotentAfterUpgrade() {
    store.associateActivity(SESSION, ACTIVITY_ID, SESSION_START)
    val original = sample(1)
    assertEquals(
        WorkoutTelemetryInboxStore.CommitStatus.COMMITTED,
        store.commitBatch(batch(original), NOW).status)
    val legacyPayload = encodeLegacyBatch(original)
    database.update(
        "relven_wear_telemetry_sample",
        ContentValues().apply { put("payload", legacyPayload) },
        "session_id = ? AND sequence = ?",
        arrayOf(SESSION, "1"))

    val replay = store.commitBatch(batch(original), NOW)

    assertEquals(WorkoutTelemetryInboxStore.CommitStatus.DUPLICATE, replay.status)
    assertEquals(1, store.sampleCount(SESSION))
  }

  private fun sample(
      sequence: Long,
      latitude: Double = 59.33,
      wallTimeMillis: Long = SESSION_START + sequence * 1_000,
      elapsedRealtimeNanos: Long = sequence * 1_000_000_000L,
      sessionId: String = SESSION
  ) =
      WorkoutTelemetryProtocol.Sample(
          sessionId,
          sequence,
          elapsedRealtimeNanos,
          wallTimeMillis,
          latitude,
          18.06,
          5.0,
          null,
          null,
          140.0)

  private fun batch(vararg samples: WorkoutTelemetryProtocol.Sample) =
      WorkoutTelemetryProtocol.Batch(SESSION, samples.toList())

  private fun batch(
      sample: WorkoutTelemetryProtocol.Sample,
      anchor: WorkoutTelemetryProtocol.ClockAnchor
  ) = WorkoutTelemetryProtocol.Batch(SESSION, listOf(sample), anchor)

  private fun clockAnchor(offsetMillis: Long, syncElapsedMillis: Long) =
      WorkoutTelemetryProtocol.ClockAnchor(
          offsetMillis,
          syncElapsedMillis * 1_000_000L,
          SESSION_START + offsetMillis + syncElapsedMillis,
          50)

  private fun encodeLegacyBatch(sample: WorkoutTelemetryProtocol.Sample): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
      output.writeInt(0x57544231)
      output.writeByte(1)
      output.writeUTF(sample.sessionId)
      output.writeByte(1)
      output.writeLong(sample.sequence)
      output.writeLong(sample.watchElapsedRealtimeNanos)
      output.writeLong(sample.wallTimeMillis)
      val flags = (if (sample.latitude != null) 1 else 0) +
          (if (sample.horizontalAccuracyMeters != null) 2 else 0) +
          (if (sample.altitudeMeters != null) 4 else 0) +
          (if (sample.speedMetersPerSecond != null) 8 else 0) +
          (if (sample.heartRateBpm != null) 16 else 0)
      output.writeByte(flags)
      if (sample.latitude != null) {
        output.writeDouble(sample.latitude)
        output.writeDouble(sample.longitude!!)
      }
      sample.horizontalAccuracyMeters?.let(output::writeDouble)
      sample.altitudeMeters?.let(output::writeDouble)
      sample.speedMetersPerSecond?.let(output::writeDouble)
      sample.heartRateBpm?.let(output::writeDouble)
    }
    return bytes.toByteArray()
  }

  private companion object {
    const val SESSION = "telemetry-session-1"
    const val ACTIVITY_ID = 42L
    const val SESSION_START = 1_800_000_000_000L
    const val NOW = SESSION_START + 60_000L
  }
}
