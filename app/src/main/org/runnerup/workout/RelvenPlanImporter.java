package org.runnerup.workout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Validates a dated, offline running plan before it can be saved or scheduled. */
public final class RelvenPlanImporter {
  private RelvenPlanImporter() {}

  public record Session(String id, String date, String kind, String workoutId, String notes) {}

  public record Plan(
      String id,
      String name,
      String timezone,
      List<Session> days,
      Map<String, Workout> workouts,
      String sourceJson) {
    public long workoutDays() {
      return days.stream().filter(day -> day.kind().equals("workout")).count();
    }

    public long restDays() {
      return days.stream().filter(day -> day.kind().equals("rest")).count();
    }
  }

  public static Plan parse(String json) {
    try {
      JSONObject root = new JSONObject(json);
      if (!"relven.plan".equals(root.getString("format")) || root.getInt("schemaVersion") != 1) {
        throw invalid("format or version");
      }
      String id = text(root, "id");
      String name = text(root, "name");
      String timezone = text(root, "timezone");
      boolean validZone = false;
      for (String zone : TimeZone.getAvailableIDs()) {
        if (zone.equals(timezone)) {
          validZone = true;
          break;
        }
      }
      if (!validZone) throw invalid("timezone");

      JSONArray workoutArray = root.getJSONArray("workouts");
      if (workoutArray.length() == 0 || workoutArray.length() > 100) {
        throw invalid("workout count");
      }
      Map<String, Workout> workouts = new HashMap<>();
      for (int i = 0; i < workoutArray.length(); i++) {
        JSONObject document = workoutArray.getJSONObject(i);
        String workoutId = text(document, "id");
        if (workouts.containsKey(workoutId)) throw invalid("duplicate workout id");
        workouts.put(workoutId, RelvenWorkoutImporter.parse(document.toString()));
      }

      JSONArray dayArray = root.getJSONArray("days");
      if (dayArray.length() == 0 || dayArray.length() > 2000) throw invalid("day count");
      Set<String> sessionIds = new HashSet<>();
      Set<String> restDates = new HashSet<>();
      Set<String> workoutDates = new HashSet<>();
      List<Session> days = new ArrayList<>();
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
      dateFormat.setLenient(false);
      for (int i = 0; i < dayArray.length(); i++) {
        JSONObject day = dayArray.getJSONObject(i);
        String sessionId = text(day, "id");
        if (!sessionIds.add(sessionId)) throw invalid("duplicate session id");
        String date = text(day, "date");
        try {
          if (!date.matches("\\d{4}-\\d{2}-\\d{2}")
              || !date.equals(dateFormat.format(dateFormat.parse(date)))) {
            throw invalid("date");
          }
        } catch (ParseException error) {
          throw invalid("date");
        }
        String kind = day.getString("kind");
        String notes = day.optString("notes", "");
        if (notes.length() > 1000) throw invalid("notes");
        String workoutId = null;
        switch (kind) {
          case "workout":
            workoutId = text(day, "workoutId");
            if (!workouts.containsKey(workoutId)) throw invalid("unknown workout: " + workoutId);
            workoutDates.add(date);
            break;
          case "rest":
            if (day.has("workoutId") || !restDates.add(date)) throw invalid("rest day");
            break;
          default:
            throw invalid("day kind");
        }
        days.add(new Session(sessionId, date, kind, workoutId, notes));
      }
      if (!Collections.disjoint(restDates, workoutDates)) {
        throw invalid("rest and workout on the same date");
      }
      return new Plan(id, name, timezone, List.copyOf(days), Map.copyOf(workouts), json);
    } catch (JSONException error) {
      throw new IllegalArgumentException("Invalid Relven plan JSON", error);
    }
  }

  private static String text(JSONObject object, String key) throws JSONException {
    String value = object.getString(key);
    if (value.isBlank() || value.length() > 160) throw invalid(key);
    return value;
  }

  private static IllegalArgumentException invalid(String what) {
    return new IllegalArgumentException("Invalid Relven plan " + what);
  }
}
