package org.runnerup.notification;

import android.annotation.SuppressLint;
import android.content.pm.ServiceInfo;
import android.os.Build;

/** Selects a declared foreground-service type supported by the app's current permissions. */
public final class WorkoutForegroundServiceTypePolicy {
  private WorkoutForegroundServiceTypePolicy() {}

  @SuppressLint("InlinedApi")
  public static int typeFor(
      int apiLevel, boolean locationPermissionGranted, boolean activityRecognitionGranted) {
    if (apiLevel < Build.VERSION_CODES.Q) return 0;

    int type = 0;
    if (locationPermissionGranted) {
      type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
    }
    if (apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && activityRecognitionGranted) {
      type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
    }
    return type;
  }

  public static boolean needsActivityRecognitionPermission(
      int apiLevel, boolean locationPermissionGranted, boolean activityRecognitionGranted) {
    return apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        && !locationPermissionGranted
        && !activityRecognitionGranted;
  }
}
