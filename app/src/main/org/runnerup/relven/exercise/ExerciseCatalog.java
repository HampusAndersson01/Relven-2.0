package org.runnerup.relven.exercise;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Offline exercise catalog with stable IDs and immutable revision snapshots. */
public final class ExerciseCatalog extends SQLiteOpenHelper {
  private static final String DB_NAME = "relven-exercises.db";
  private static final int DB_VERSION = 1;
  private static final String ASSET = "relven/exercises-v1.json";
  private final Context context;

  public record Exercise(
      String id,
      String sourceId,
      String name,
      String instructionsJson,
      String category,
      String equipment,
      String primaryMusclesJson,
      String secondaryMusclesJson,
      String movementType,
      String difficulty,
      String source,
      int revision,
      boolean custom,
      boolean favorite) {}

  public ExerciseCatalog(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
    this.context = context.getApplicationContext();
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE exercise (id TEXT PRIMARY KEY, source_id TEXT UNIQUE, name TEXT NOT NULL, "
            + "swedish_name TEXT, description TEXT, instructions_json TEXT NOT NULL, category TEXT, "
            + "equipment TEXT, primary_muscles_json TEXT NOT NULL, secondary_muscles_json TEXT NOT NULL, "
            + "movement_type TEXT, difficulty TEXT, media_json TEXT, tags_json TEXT, source TEXT NOT NULL, "
            + "source_revision TEXT, revision INTEGER NOT NULL, custom INTEGER NOT NULL DEFAULT 0, "
            + "favorite INTEGER NOT NULL DEFAULT 0, created_at_ms INTEGER NOT NULL, modified_at_ms INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE exercise_revision (exercise_id TEXT NOT NULL, revision INTEGER NOT NULL, "
            + "snapshot_json TEXT NOT NULL, PRIMARY KEY(exercise_id, revision))");
    db.execSQL("CREATE INDEX exercise_name_index ON exercise(name)");
    try {
      seed(db);
    } catch (IOException | JSONException error) {
      throw new IllegalStateException("Bundled exercise catalog is invalid", error);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    throw new IllegalStateException("Exercise catalog migration required");
  }

  private void seed(SQLiteDatabase db) throws IOException, JSONException {
    StringBuilder json = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        context.getAssets().open(ASSET), StandardCharsets.UTF_8))) {
      char[] buffer = new char[8192];
      int length;
      while ((length = reader.read(buffer)) != -1) json.append(buffer, 0, length);
    }
    JSONArray items = new JSONArray(json.toString());
    if (items.length() < 800) throw new IllegalStateException("Incomplete exercise asset");
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.getJSONObject(i);
      ContentValues values = new ContentValues();
      values.put("id", item.getString("id"));
      values.put("source_id", item.getString("sourceId"));
      values.put("name", item.getString("name"));
      values.put("instructions_json", item.getJSONArray("instructions").toString());
      values.put("category", nullableText(item, "category"));
      values.put("equipment", nullableText(item, "equipment"));
      values.put("primary_muscles_json", item.getJSONArray("primaryMuscles").toString());
      values.put("secondary_muscles_json", item.getJSONArray("secondaryMuscles").toString());
      values.put("movement_type", nullableText(item, "movementType"));
      values.put("difficulty", nullableText(item, "difficulty"));
      values.put("source", item.getString("source"));
      values.put("source_revision", item.getString("sourceRevision"));
      values.put("revision", item.getInt("revision"));
      values.put("created_at_ms", 0L);
      values.put("modified_at_ms", 0L);
      db.insertOrThrow("exercise", null, values);
      ContentValues revision = new ContentValues();
      revision.put("exercise_id", item.getString("id"));
      revision.put("revision", 1);
      revision.put("snapshot_json", item.toString());
      db.insertOrThrow("exercise_revision", null, revision);
    }
  }

  public List<Exercise> search(String query, String category, String equipment, int limit) {
    return search(query, category, equipment, limit, false);
  }

  public List<Exercise> search(
      String query, String category, String equipment, int limit, boolean favoritesOnly) {
    if (limit < 1 || limit > 200) throw new IllegalArgumentException("Invalid search limit");
    StringBuilder where = new StringBuilder("1 = 1");
    List<String> args = new ArrayList<>();
    if (favoritesOnly) where.append(" AND favorite = 1");
    if (query != null && !query.isBlank()) {
      where.append(" AND name LIKE ? ESCAPE '\\'");
      args.add("%" + query.replace("\\", "\\\\").replace("%", "\\%")
          .replace("_", "\\_") + "%");
    }
    if (category != null && !category.isBlank()) {
      where.append(" AND category = ?");
      args.add(category);
    }
    if (equipment != null && !equipment.isBlank()) {
      where.append(" AND equipment = ?");
      args.add(equipment);
    }
    List<Exercise> result = new ArrayList<>();
    try (Cursor cursor = getReadableDatabase().query("exercise", null, where.toString(),
        args.toArray(new String[0]), null, null, "name COLLATE NOCASE", Integer.toString(limit))) {
      while (cursor.moveToNext()) result.add(fromCursor(cursor));
    }
    return result;
  }

  public Exercise find(String id) {
    try (Cursor cursor = getReadableDatabase().query("exercise", null, "id = ?",
        new String[] {id}, null, null, null)) {
      return cursor.moveToFirst() ? fromCursor(cursor) : null;
    }
  }

  public String createCustom(String name, List<String> instructions) {
    validateCustom(name, instructions);
    String id = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    JSONObject snapshot = new JSONObject();
    try {
      snapshot.put("id", id);
      snapshot.put("name", name.trim());
      snapshot.put("instructions", new JSONArray(instructions));
      snapshot.put("revision", 1);
      snapshot.put("source", "local");
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid custom exercise", error);
    }
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("id", id);
      values.put("name", name.trim());
      values.put("instructions_json", new JSONArray(instructions).toString());
      values.put("primary_muscles_json", "[]");
      values.put("secondary_muscles_json", "[]");
      values.put("source", "local");
      values.put("revision", 1);
      values.put("custom", 1);
      values.put("created_at_ms", now);
      values.put("modified_at_ms", now);
      db.insertOrThrow("exercise", null, values);
      ContentValues revision = new ContentValues();
      revision.put("exercise_id", id);
      revision.put("revision", 1);
      revision.put("snapshot_json", snapshot.toString());
      db.insertOrThrow("exercise_revision", null, revision);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return id;
  }

  public void updateCustom(String id, String name, List<String> instructions) {
    validateCustom(name, instructions);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      int previousRevision;
      try (Cursor cursor = db.query("exercise", new String[] {"revision"},
          "id = ? AND custom = 1", new String[] {id}, null, null, null)) {
        if (!cursor.moveToFirst()) throw new IllegalArgumentException("Unknown custom exercise ID");
        previousRevision = cursor.getInt(0);
      }
      int nextRevision = previousRevision + 1;
      ContentValues values = new ContentValues();
      values.put("name", name.trim());
      values.put("instructions_json", new JSONArray(instructions).toString());
      values.put("revision", nextRevision);
      values.put("modified_at_ms", System.currentTimeMillis());
      db.update("exercise", values, "id = ?", new String[] {id});
      JSONObject snapshot = new JSONObject();
      snapshot.put("id", id);
      snapshot.put("name", name.trim());
      snapshot.put("instructions", new JSONArray(instructions));
      snapshot.put("revision", nextRevision);
      snapshot.put("source", "local");
      ContentValues revision = new ContentValues();
      revision.put("exercise_id", id);
      revision.put("revision", nextRevision);
      revision.put("snapshot_json", snapshot.toString());
      db.insertOrThrow("exercise_revision", null, revision);
      db.setTransactionSuccessful();
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid custom exercise", error);
    } finally {
      db.endTransaction();
    }
  }

  public String getRevisionSnapshot(String id, int revision) {
    try (Cursor cursor = getReadableDatabase().query("exercise_revision",
        new String[] {"snapshot_json"}, "exercise_id = ? AND revision = ?",
        new String[] {id, Integer.toString(revision)}, null, null, null)) {
      return cursor.moveToFirst() ? cursor.getString(0) : null;
    }
  }

  public void setFavorite(String id, boolean favorite) {
    ContentValues values = new ContentValues();
    values.put("favorite", favorite ? 1 : 0);
    values.put("modified_at_ms", System.currentTimeMillis());
    if (getWritableDatabase().update("exercise", values, "id = ?", new String[] {id}) == 0) {
      throw new IllegalArgumentException("Unknown exercise ID");
    }
  }

  private static String nullableText(JSONObject object, String key) {
    return object.isNull(key) ? null : object.optString(key, null);
  }

  private static void validateCustom(String name, List<String> instructions) {
    if (name == null || name.isBlank() || name.length() > 120 || instructions == null
        || instructions.isEmpty() || instructions.size() > 30) {
      throw new IllegalArgumentException("Invalid custom exercise");
    }
    for (String instruction : instructions) {
      if (instruction == null || instruction.isBlank() || instruction.length() > 1000) {
        throw new IllegalArgumentException("Invalid custom exercise instruction");
      }
    }
  }

  private static Exercise fromCursor(Cursor cursor) {
    return new Exercise(
        value(cursor, "id"), value(cursor, "source_id"), value(cursor, "name"),
        value(cursor, "instructions_json"), value(cursor, "category"), value(cursor, "equipment"),
        value(cursor, "primary_muscles_json"), value(cursor, "secondary_muscles_json"),
        value(cursor, "movement_type"), value(cursor, "difficulty"), value(cursor, "source"),
        cursor.getInt(cursor.getColumnIndexOrThrow("revision")),
        cursor.getInt(cursor.getColumnIndexOrThrow("custom")) != 0,
        cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) != 0);
  }

  private static String value(Cursor cursor, String column) {
    int index = cursor.getColumnIndexOrThrow(column);
    return cursor.isNull(index) ? null : cursor.getString(index);
  }
}
