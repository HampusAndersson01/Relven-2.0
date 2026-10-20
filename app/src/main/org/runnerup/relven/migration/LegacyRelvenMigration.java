package org.runnerup.relven.migration;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.runnerup.workout.RelvenWorkoutImporter;

/** Streams and classifies data in the read-only archive of the old Relven database. */
public final class LegacyRelvenMigration {
  private static final String SOURCE_DATABASE = "relven-workouts.db";
  private static final Set<String> WORKOUT_COLUMNS =
      Set.of("serverOrigin", "ownerUserId", "workoutId", "revision", "documentJson");
  private static final Set<String> ACTIVITY_COLUMNS =
      Set.of(
          "serverOrigin",
          "ownerUserId",
          "serverActivityId",
          "clientActivityId",
          "activityJson",
          "documentJson",
          "syncState");
  private static final Set<String> PLAN_COLUMNS = Set.of("scheduleMovesJson", "scheduleSkipsJson");

  private LegacyRelvenMigration() {}

  public static Preview preview(Context context) throws IOException {
    if (!LegacyRelvenVault.captureIfPresent(context)) {
      throw new IOException("No legacy Relven archive is available");
    }
    File archive = LegacyRelvenVault.snapshotFile(context);
    try (FileInputStream stream = new FileInputStream(archive);
        InputStreamReader input = new InputStreamReader(stream, StandardCharsets.UTF_8);
        JsonReader reader = new JsonReader(input)) {
      reader.beginObject();
      int schemaVersion = -1;
      String snapshotType = null;
      String sourceDatabase = null;
      boolean foundTables = false;
      PreviewCounter counter = new PreviewCounter();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "schemaVersion" -> schemaVersion = reader.nextInt();
          case "snapshotType" -> snapshotType = readNullableString(reader);
          case "sourceDatabase" -> sourceDatabase = readNullableString(reader);
          case "tables" -> {
            if (foundTables)
              throw new IOException("Legacy archive contains duplicate tables arrays");
            foundTables = true;
            readTables(reader, counter);
          }
          default -> reader.skipValue();
        }
      }
      reader.endObject();
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IOException("Legacy archive contains trailing data");
      }
      if (schemaVersion != 1
          || !"table-row-archive".equals(snapshotType)
          || !SOURCE_DATABASE.equals(sourceDatabase)
          || !foundTables) {
        throw new IOException("Legacy archive header is unsupported or incomplete");
      }
      return counter.toPreview();
    } catch (IllegalStateException e) {
      throw new IOException("Legacy archive is malformed", e);
    }
  }

  private static void readTables(JsonReader reader, PreviewCounter counter) throws IOException {
    reader.beginArray();
    while (reader.hasNext()) readTable(reader, counter);
    reader.endArray();
  }

  private static void readTable(JsonReader reader, PreviewCounter counter) throws IOException {
    reader.beginObject();
    String tableName = null;
    List<String> columns = null;
    while (reader.hasNext()) {
      String property = reader.nextName();
      switch (property) {
        case "name" -> tableName = readNullableString(reader);
        case "columns" -> columns = readStringArray(reader);
        case "rows" -> {
          Set<String> wanted = wantedColumns(tableName);
          if (wanted == null) {
            reader.skipValue();
          } else {
            if (columns == null)
              throw new IOException("Legacy table columns precede rows requirement failed");
            readRows(reader, tableName, columns, wanted, counter);
          }
        }
        default -> reader.skipValue();
      }
    }
    reader.endObject();
  }

  private static Set<String> wantedColumns(String table) {
    if (table == null) return null;
    return switch (table) {
      case "cached_workout" -> WORKOUT_COLUMNS;
      case "cached_activity", "activity_outbox" -> ACTIVITY_COLUMNS;
      case "cached_plan" -> PLAN_COLUMNS;
      case "active_workout" -> Set.of();
      case "plan_mutation",
          "plan_schedule_move_outbox",
          "plan_schedule_skip_outbox",
          "activity_lifecycle_outbox" ->
          Set.of("state");
      default -> null;
    };
  }

  private static List<String> readStringArray(JsonReader reader) throws IOException {
    List<String> values = new ArrayList<>();
    reader.beginArray();
    while (reader.hasNext()) values.add(readNullableString(reader));
    reader.endArray();
    return values;
  }

  private static void readRows(
      JsonReader reader,
      String table,
      List<String> columns,
      Set<String> wanted,
      PreviewCounter counter)
      throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      Map<String, String> values = readRow(reader, columns, wanted);
      counter.accept(table, values);
    }
    reader.endArray();
  }

  private static Map<String, String> readRow(
      JsonReader reader, List<String> columns, Set<String> wanted) throws IOException {
    Map<String, String> values = new HashMap<>();
    reader.beginArray();
    int index = 0;
    while (reader.hasNext()) {
      String column = index < columns.size() ? columns.get(index) : null;
      if (column != null && wanted.contains(column)) {
        values.put(column, readNullableString(reader));
      } else {
        reader.skipValue();
      }
      index++;
    }
    reader.endArray();
    return values;
  }

  private static String readNullableString(JsonReader reader) throws IOException {
    JsonToken token = reader.peek();
    if (token == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    if (token == JsonToken.STRING || token == JsonToken.NUMBER) return reader.nextString();
    reader.skipValue();
    return null;
  }

  private static final class PreviewCounter {
    private int supportedWorkouts;
    private int unsupportedWorkouts;
    private int supportedActivities;
    private int unsupportedActivities;
    private int plans;
    private int activityRecords;
    private int activeSessions;
    private int scheduleChanges;
    private int pendingOperations;
    private long activityOrdinal;
    private final Set<String> seenActivities = new HashSet<>();

    void accept(String table, Map<String, String> row) {
      switch (table) {
        case "cached_workout" -> countWorkout(row);
        case "cached_activity" -> countActivity(row);
        case "activity_outbox" -> {
          countActivity(row);
          if ("pending".equals(row.get("syncState"))) pendingOperations++;
        }
        case "cached_plan" -> countPlan(row);
        case "active_workout" -> activeSessions++;
        case "plan_mutation",
            "plan_schedule_move_outbox",
            "plan_schedule_skip_outbox",
            "activity_lifecycle_outbox" -> {
          if ("pending".equals(row.get("state"))) pendingOperations++;
        }
      }
    }

    private void countWorkout(Map<String, String> row) {
      String origin = row.get("serverOrigin");
      String owner = row.get("ownerUserId");
      String workoutId = row.get("workoutId");
      String revision = row.get("revision");
      String document = row.get("documentJson");
      if (isBlank(origin)
          || isBlank(owner)
          || isBlank(workoutId)
          || isBlank(revision)
          || isBlank(document)) {
        unsupportedWorkouts++;
        return;
      }
      try {
        if (Integer.parseInt(revision) < 0) throw new IllegalArgumentException("Invalid revision");
        RelvenWorkoutImporter.parse(document);
        supportedWorkouts++;
      } catch (RuntimeException e) {
        unsupportedWorkouts++;
      }
    }

    private void countActivity(Map<String, String> row) {
      String document = row.get("activityJson");
      if (document == null) document = row.get("documentJson");
      String rowClientId = row.get("clientActivityId");
      LegacyRelvenActivityDocument parsed = null;
      if (!isBlank(document)) {
        try {
          parsed = LegacyRelvenActivityDocument.parse(document);
        } catch (RuntimeException ignored) {
          // Unsupported source records are counted and remain intact in the archive.
        }
      }
      String clientId =
          firstNonBlank(rowClientId, parsed == null ? null : parsed.getClientActivityId());
      String serverId = row.get("serverActivityId");
      String identity =
          !isBlank(clientId)
              ? "client:" + clientId
              : !isBlank(serverId)
                  ? String.join(
                      "\u0000",
                      "server",
                      nullToEmpty(row.get("serverOrigin")),
                      nullToEmpty(row.get("ownerUserId")),
                      serverId)
                  : "row:" + (++activityOrdinal);
      if (!seenActivities.add(identity)) return;
      activityRecords++;
      if (parsed != null
          && (isBlank(rowClientId)
              || isBlank(parsed.getClientActivityId())
              || rowClientId.equals(parsed.getClientActivityId()))) {
        supportedActivities++;
      } else {
        unsupportedActivities++;
      }
    }

    private void countPlan(Map<String, String> row) {
      plans++;
      scheduleChanges += jsonArrayLength(row.get("scheduleMovesJson"));
      scheduleChanges += jsonArrayLength(row.get("scheduleSkipsJson"));
    }

    Preview toPreview() {
      return new Preview(
          supportedWorkouts,
          unsupportedWorkouts,
          supportedActivities,
          unsupportedActivities,
          plans,
          activityRecords,
          activeSessions,
          scheduleChanges,
          pendingOperations);
    }
  }

  private static int jsonArrayLength(String source) {
    if (isBlank(source)) return 0;
    try {
      return new JSONArray(source).length();
    } catch (JSONException e) {
      return 0;
    }
  }

  private static String firstNonBlank(String first, String second) {
    return !isBlank(first) ? first : second;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public static final class Preview {
    private final int supportedWorkoutCount;
    private final int unsupportedWorkoutCount;
    private final int supportedActivityCount;
    private final int unsupportedActivityCount;
    private final int planCount;
    private final int activityCount;
    private final int activeSessionCount;
    private final int scheduleChangeCount;
    private final int pendingOperationCount;

    private Preview(
        int supportedWorkoutCount,
        int unsupportedWorkoutCount,
        int supportedActivityCount,
        int unsupportedActivityCount,
        int planCount,
        int activityCount,
        int activeSessionCount,
        int scheduleChangeCount,
        int pendingOperationCount) {
      this.supportedWorkoutCount = supportedWorkoutCount;
      this.unsupportedWorkoutCount = unsupportedWorkoutCount;
      this.supportedActivityCount = supportedActivityCount;
      this.unsupportedActivityCount = unsupportedActivityCount;
      this.planCount = planCount;
      this.activityCount = activityCount;
      this.activeSessionCount = activeSessionCount;
      this.scheduleChangeCount = scheduleChangeCount;
      this.pendingOperationCount = pendingOperationCount;
    }

    public int getSupportedWorkoutCount() {
      return supportedWorkoutCount;
    }

    public int getUnsupportedWorkoutCount() {
      return unsupportedWorkoutCount;
    }

    public int getSupportedActivityCount() {
      return supportedActivityCount;
    }

    public int getUnsupportedActivityCount() {
      return unsupportedActivityCount;
    }

    public int getPlanCount() {
      return planCount;
    }

    public int getActivityCount() {
      return activityCount;
    }

    public int getActiveSessionCount() {
      return activeSessionCount;
    }

    public int getScheduleChangeCount() {
      return scheduleChangeCount;
    }

    public int getPendingOperationCount() {
      return pendingOperationCount;
    }
  }
}
