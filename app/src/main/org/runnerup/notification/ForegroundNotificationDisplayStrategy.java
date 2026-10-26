package org.runnerup.notification;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

public class ForegroundNotificationDisplayStrategy implements NotificationDisplayStrategy {
  private final Service service;

  public ForegroundNotificationDisplayStrategy(Service service) {
    this.service = service;
  }

  private boolean isForeground = false;

  @Override
  public void notify(int notificationId, Notification notification) {
    if (!isForeground) {
      boolean locationGranted =
          ContextCompat.checkSelfPermission(service, Manifest.permission.ACCESS_FINE_LOCATION)
                  == PackageManager.PERMISSION_GRANTED
              || ContextCompat.checkSelfPermission(
                      service, Manifest.permission.ACCESS_COARSE_LOCATION)
                  == PackageManager.PERMISSION_GRANTED;
      boolean activityRecognitionGranted =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
              && ContextCompat.checkSelfPermission(
                      service, Manifest.permission.ACTIVITY_RECOGNITION)
                  == PackageManager.PERMISSION_GRANTED;
      int type =
          WorkoutForegroundServiceTypePolicy.typeFor(
              Build.VERSION.SDK_INT, locationGranted, activityRecognitionGranted);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && type == 0) {
        throw new IllegalStateException(
            "Starting the workout foreground service requires location or activity recognition permission");
      }
      ServiceCompat.startForeground(service, notificationId, notification, type);
      isForeground = true;
    } else {
      android.app.NotificationManager notificationManager =
          ContextCompat.getSystemService(service, android.app.NotificationManager.class);
      notificationManager.notify(notificationId, notification);
    }
  }

  @Override
  public void cancel(int notificationId) {
    isForeground = false;
    ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE);
    // Updates after the first are posted with notify(), and the service may already have
    // left the foreground (RunActivity.doStop), so remove the notification explicitly too.
    android.app.NotificationManager notificationManager =
        ContextCompat.getSystemService(service, android.app.NotificationManager.class);
    notificationManager.cancel(notificationId);
  }
}
