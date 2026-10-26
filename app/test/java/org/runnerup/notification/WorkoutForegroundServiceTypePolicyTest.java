package org.runnerup.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ServiceInfo;
import org.junit.Test;

public class WorkoutForegroundServiceTypePolicyTest {
  @Test
  public void api34AndLaterUsesLocationWhenLocationPermissionIsGranted() {
    assertEquals(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        WorkoutForegroundServiceTypePolicy.typeFor(36, true, false));
    assertFalse(
        WorkoutForegroundServiceTypePolicy.needsActivityRecognitionPermission(36, true, false));
  }

  @Test
  public void api34AndLaterUsesHealthWhenGpsPermissionIsNotAvailable() {
    assertEquals(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        WorkoutForegroundServiceTypePolicy.typeFor(36, false, true));
    assertFalse(
        WorkoutForegroundServiceTypePolicy.needsActivityRecognitionPermission(36, false, true));
    assertTrue(
        WorkoutForegroundServiceTypePolicy.needsActivityRecognitionPermission(36, false, false));
  }

  @Test
  public void legacyPlatformsDoNotRequireActivityRecognitionForForegroundType() {
    assertEquals(0, WorkoutForegroundServiceTypePolicy.typeFor(33, false, false));
    assertFalse(
        WorkoutForegroundServiceTypePolicy.needsActivityRecognitionPermission(33, false, false));
  }
}
