package org.runnerup.relven.telemetry

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.common.util.Constants.DB
import org.runnerup.relven.ui.readActivityRoute

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkoutSessionRouteBoundaryReconcilerTest {
  private val database = SQLiteDatabase.create(null).apply {
    execSQL(
        "CREATE TABLE ${DB.ACTIVITY.TABLE} (" +
            "_id INTEGER PRIMARY KEY, ${DB.ACTIVITY.DISTANCE} REAL, ${DB.ACTIVITY.TIME} INTEGER, " +
            "${DB.ACTIVITY.AVG_HR} INTEGER, ${DB.ACTIVITY.MAX_HR} INTEGER, " +
            "${DB.ACTIVITY.DELETED} INTEGER NOT NULL DEFAULT 0)")
    execSQL(
        "CREATE TABLE ${DB.LOCATION.TABLE} (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, ${DB.LOCATION.ACTIVITY} INTEGER NOT NULL, " +
            "${DB.LOCATION.LAP} INTEGER NOT NULL, ${DB.LOCATION.TYPE} INTEGER NOT NULL, " +
            "${DB.LOCATION.TIME} INTEGER NOT NULL, ${DB.LOCATION.LATITUDE} REAL NOT NULL, " +
            "${DB.LOCATION.LONGITUDE} REAL NOT NULL, ${DB.LOCATION.HR} INTEGER, " +
            "${DB.LOCATION.ALTITUDE} REAL, ${DB.LOCATION.SPEED} REAL, ${DB.LOCATION.CADENCE} REAL, " +
            "${DB.LOCATION.ACCURANCY} REAL, ${DB.LOCATION.ELAPSED} REAL, ${DB.LOCATION.DISTANCE} REAL)")
    execSQL(
        "CREATE TABLE ${DB.LAP.TABLE} (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, ${DB.LAP.ACTIVITY} INTEGER NOT NULL, " +
            "${DB.LAP.LAP} INTEGER NOT NULL, ${DB.LAP.DISTANCE} REAL, ${DB.LAP.TIME} INTEGER, " +
            "${DB.LAP.AVG_HR} INTEGER, ${DB.LAP.MAX_HR} INTEGER)")
    insert(DB.ACTIVITY.TABLE, null, ContentValues().apply { put("_id", ACTIVITY_ID) })
    insert(DB.LAP.TABLE, null, ContentValues().apply {
      put(DB.LAP.ACTIVITY, ACTIVITY_ID)
      put(DB.LAP.LAP, 0)
    })
    WorkoutTelemetryInboxStore.ensureSchema(this)
    WorkoutTelemetryRouteReconciler.ensureSchema(this)
  }

  @After
  fun closeDatabase() = database.close()

  @Test
  fun delayedPauseIsPlacedAtItsOriginalTimeAndLastActiveFix() {
    insertLocation(DB.LOCATION.TYPE_START, 1_000L, 59.0, 18.0)
    insertLocation(DB.LOCATION.TYPE_GPS, 2_000L, 59.0001, 18.0001)
    insertLocation(DB.LOCATION.TYPE_GPS, 3_000L, 59.0002, 18.0002)
    val delayedPauseId = insertLocation(DB.LOCATION.TYPE_PAUSE, 5_000L, 59.0002, 18.0002)

    val applied =
        WorkoutSessionRouteBoundaryReconciler(database)
            .backdateBoundary(ACTIVITY_ID, delayedPauseId, 2_500L)

    assertTrue(applied)
    assertEquals(1, readActivityRoute(database, ACTIVITY_ID).size)
    database.query(
        DB.LOCATION.TABLE,
        arrayOf(DB.LOCATION.TIME, DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE),
        "_id = ?",
        arrayOf(delayedPauseId.toString()),
        null,
        null,
        null).use { cursor ->
          assertTrue(cursor.moveToFirst())
          assertEquals(2_500L, cursor.getLong(0))
          assertEquals(59.0001, cursor.getDouble(1), 0.000001)
          assertEquals(18.0001, cursor.getDouble(2), 0.000001)
        }
    database.query(DB.ACTIVITY.TABLE, arrayOf(DB.ACTIVITY.TIME), "_id = ?",
        arrayOf(ACTIVITY_ID.toString()), null, null, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(2L, cursor.getLong(0))
    }
  }

  @Test
  fun delayedResumeStartsANewRouteSegmentAtTheFirstActiveFix() {
    insertLocation(DB.LOCATION.TYPE_START, 1_000L, 59.0, 18.0)
    insertLocation(DB.LOCATION.TYPE_GPS, 2_000L, 59.0001, 18.0001)
    val pauseId = insertLocation(DB.LOCATION.TYPE_PAUSE, 2_500L, 59.0001, 18.0001)
    insertLocation(DB.LOCATION.TYPE_GPS, 3_000L, 59.0002, 18.0002)
    insertLocation(DB.LOCATION.TYPE_GPS, 4_000L, 59.0003, 18.0003)
    val delayedResumeId = insertLocation(DB.LOCATION.TYPE_RESUME, 6_000L, 59.0003, 18.0003)

    val reconciler = WorkoutSessionRouteBoundaryReconciler(database)
    assertTrue(reconciler.backdateBoundary(ACTIVITY_ID, pauseId, 2_500L))
    assertTrue(reconciler.backdateBoundary(ACTIVITY_ID, delayedResumeId, 3_500L))

    val route = readActivityRoute(database, ACTIVITY_ID)
    assertEquals(2, route.size)
    assertEquals(0, route[0].segment)
    assertEquals(2, route[1].segment)
    database.query(DB.ACTIVITY.TABLE, arrayOf(DB.ACTIVITY.TIME), "_id = ?",
        arrayOf(ACTIVITY_ID.toString()), null, null, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(2L, cursor.getLong(0))
    }
  }

  @Test
  fun delayedBoundaryStillCorrectsActiveDurationWhenNoGpsAnchorExists() {
    insertLocation(DB.LOCATION.TYPE_START, 1_000L, 59.0, 18.0)
    val delayedPauseId = insertLocation(DB.LOCATION.TYPE_PAUSE, 5_000L, 59.0, 18.0)

    assertTrue(
        WorkoutSessionRouteBoundaryReconciler(database)
            .backdateBoundary(ACTIVITY_ID, delayedPauseId, 2_000L))

    database.query(DB.ACTIVITY.TABLE, arrayOf(DB.ACTIVITY.TIME), "_id = ?",
        arrayOf(ACTIVITY_ID.toString()), null, null, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1L, cursor.getLong(0))
    }
  }

  private fun insertLocation(type: Int, time: Long, latitude: Double, longitude: Double): Long =
      database.insertOrThrow(
          DB.LOCATION.TABLE,
          null,
          ContentValues().apply {
            put(DB.LOCATION.ACTIVITY, ACTIVITY_ID)
            put(DB.LOCATION.LAP, 0)
            put(DB.LOCATION.TYPE, type)
            put(DB.LOCATION.TIME, time)
            put(DB.LOCATION.LATITUDE, latitude)
            put(DB.LOCATION.LONGITUDE, longitude)
          })

  private companion object {
    const val ACTIVITY_ID = 17L
  }
}
