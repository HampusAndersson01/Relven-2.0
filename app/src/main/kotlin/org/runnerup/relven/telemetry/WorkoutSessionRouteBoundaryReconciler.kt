package org.runnerup.relven.telemetry

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.runnerup.common.util.Constants.DB
import org.runnerup.db.ActivityCleaner

/** Moves a just-recorded phone pause/resume marker back to the watch event's captured time. */
class WorkoutSessionRouteBoundaryReconciler(private val database: SQLiteDatabase) {
  fun backdateBoundary(activityId: Long, markerId: Long, eventTimeMillis: Long): Boolean {
    if (activityId <= 0L || markerId <= 0L || eventTimeMillis <= 0L) return false
    database.beginTransaction()
    return try {
      val marker =
          database.query(
              DB.LOCATION.TABLE,
              arrayOf(DB.LOCATION.TYPE, DB.LOCATION.TIME),
              "_id = ? AND ${DB.LOCATION.ACTIVITY} = ?",
              arrayOf(markerId.toString(), activityId.toString()),
              null,
              null,
              null,
              "1").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getLong(1) else null
              } ?: return false
      val markerType = marker.first
      if (markerType != DB.LOCATION.TYPE_PAUSE && markerType != DB.LOCATION.TYPE_RESUME) {
        return false
      }
      if (eventTimeMillis > marker.second || !isInsideWorkout(activityId, eventTimeMillis)) {
        return false
      }
      if (!boundaryOrderAllows(activityId, markerId, eventTimeMillis)) return false

      val values = ContentValues().apply {
        put(DB.LOCATION.TIME, eventTimeMillis)
        routeAnchor(activityId, eventTimeMillis, markerType)?.let { anchor ->
          put(DB.LOCATION.LATITUDE, anchor.latitude)
          put(DB.LOCATION.LONGITUDE, anchor.longitude)
          put(DB.LOCATION.LAP, anchor.lap)
          if (anchor.accuracyMeters != null) put(DB.LOCATION.ACCURANCY, anchor.accuracyMeters)
          anchor.elapsedMillis?.let { put(DB.LOCATION.ELAPSED, it) }
          anchor.distanceMeters?.let { put(DB.LOCATION.DISTANCE, it) }
        }
      }
      if (database.update(DB.LOCATION.TABLE, values, "_id = ?", arrayOf(markerId.toString())) != 1) {
        return false
      }
      ActivityCleaner().recompute(database, activityId)
      database.setTransactionSuccessful()
      true
    } catch (_: RuntimeException) {
      false
    } finally {
      if (database.inTransaction()) database.endTransaction()
    }
  }

  private data class RouteAnchor(
      val latitude: Double,
      val longitude: Double,
      val lap: Int,
      val accuracyMeters: Double?,
      val elapsedMillis: Double?,
      val distanceMeters: Double?)

  private fun isInsideWorkout(activityId: Long, eventTimeMillis: Long): Boolean =
      database.query(
          DB.LOCATION.TABLE,
          arrayOf("MIN(${DB.LOCATION.TIME})"),
          "${DB.LOCATION.ACTIVITY} = ? AND ${DB.LOCATION.TYPE} = ?",
          arrayOf(activityId.toString(), DB.LOCATION.TYPE_START.toString()),
          null,
          null,
          null).use { cursor ->
            cursor.moveToFirst() && !cursor.isNull(0) && eventTimeMillis >= cursor.getLong(0)
          }

  private fun boundaryOrderAllows(activityId: Long, markerId: Long, eventTimeMillis: Long): Boolean =
      database.query(
          DB.LOCATION.TABLE,
          arrayOf(DB.LOCATION.TIME),
          "${DB.LOCATION.ACTIVITY} = ? AND _id < ? AND ${DB.LOCATION.TYPE} IN (?, ?, ?, ?)",
          arrayOf(
              activityId.toString(),
              markerId.toString(),
              DB.LOCATION.TYPE_START.toString(),
              DB.LOCATION.TYPE_PAUSE.toString(),
              DB.LOCATION.TYPE_RESUME.toString(),
              DB.LOCATION.TYPE_END.toString()),
          null,
          null,
          "_id DESC",
          "1").use { cursor -> !cursor.moveToFirst() || cursor.getLong(0) <= eventTimeMillis }

  private fun routeAnchor(activityId: Long, eventTimeMillis: Long, markerType: Int): RouteAnchor? {
    val after = markerType == DB.LOCATION.TYPE_RESUME
    val comparison = if (after) ">=" else "<="
    val direction = if (after) "ASC" else "DESC"
    val columns =
        arrayOf(
            DB.LOCATION.LATITUDE,
            DB.LOCATION.LONGITUDE,
            DB.LOCATION.LAP,
            DB.LOCATION.ACCURANCY,
            DB.LOCATION.ELAPSED,
            DB.LOCATION.DISTANCE)
    val where =
        "${DB.LOCATION.ACTIVITY} = ? AND ${DB.LOCATION.TYPE} = ? AND ${DB.LOCATION.TIME} $comparison ?"
    val arguments =
        arrayOf(activityId.toString(), DB.LOCATION.TYPE_GPS.toString(), eventTimeMillis.toString())
    val primary = readAnchor(columns, where, arguments, "${DB.LOCATION.TIME} $direction, _id $direction")
    if (primary != null || !after) return primary
    return readAnchor(columns, where.replace(" >= ?", " <= ?"), arguments,
        "${DB.LOCATION.TIME} DESC, _id DESC")
  }

  private fun readAnchor(
      columns: Array<String>,
      selection: String,
      arguments: Array<String>,
      order: String): RouteAnchor? =
      database.query(DB.LOCATION.TABLE, columns, selection, arguments, null, null, order, "1").use {
        cursor ->
        if (!cursor.moveToFirst()) return@use null
        RouteAnchor(
            latitude = cursor.getDouble(0),
            longitude = cursor.getDouble(1),
            lap = cursor.getInt(2),
            accuracyMeters = if (cursor.isNull(3)) null else cursor.getDouble(3),
            elapsedMillis = if (cursor.isNull(4)) null else cursor.getDouble(4),
            distanceMeters = if (cursor.isNull(5)) null else cursor.getDouble(5))
      }
}
