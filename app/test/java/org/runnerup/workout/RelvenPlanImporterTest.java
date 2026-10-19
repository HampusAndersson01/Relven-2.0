package org.runnerup.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RelvenPlanImporterTest {
  private static final String RUN =
      "{\"schemaVersion\":3,\"id\":\"run-a\",\"name\":\"Easy run\",\"sport\":\"running\",\"steps\":[{\"id\":\"step-a\",\"kind\":\"segment\",\"phase\":\"work\",\"end\":{\"type\":\"time\",\"seconds\":1800}}]}";
  private static final String HEADER =
      "{\"format\":\"relven.plan\",\"schemaVersion\":1,\"id\":\"plan-1\",\"name\":\"First weeks\",\"timezone\":\"Europe/Stockholm\",\"workouts\":["
          + RUN
          + "],\"days\":";

  @Test
  public void acceptsDatedRunningSessionsAndRestDay() {
    String json =
        HEADER
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"},{\"id\":\"s2\",\"date\":\"2026-10-21\",\"kind\":\"rest\"},{\"id\":\"s3\",\"date\":\"2026-10-27\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    RelvenPlanImporter.Plan plan = RelvenPlanImporter.parse(json);
    assertEquals("First weeks", plan.name());
    assertEquals(2, plan.workoutDays());
    assertEquals(1, plan.restDays());
    assertEquals("Easy run", plan.workoutNames().get("run-a"));
  }

  @Test
  public void rejectsUnknownWorkoutReference() {
    String json =
        HEADER
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"missing\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsRestAndWorkoutOnSameDay() {
    String json =
        HEADER
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"rest\"},{\"id\":\"s2\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsInvalidDate() {
    String json = HEADER + "[{\"id\":\"s1\",\"date\":\"2026-02-30\",\"kind\":\"rest\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }

  @Test
  public void rejectsPlanContainingUnsupportedStrengthWorkout() {
    String json =
        HEADER.replace("\"sport\":\"running\"", "\"sport\":\"strength\"")
            + "[{\"id\":\"s1\",\"date\":\"2026-10-20\",\"kind\":\"workout\",\"workoutId\":\"run-a\"}]}";
    assertThrows(IllegalArgumentException.class, () -> RelvenPlanImporter.parse(json));
  }
}
