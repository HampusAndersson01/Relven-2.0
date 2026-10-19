package org.runnerup.relven.map;

import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants.DB;

/** Derives route metrics only from consecutive, plausible moving GPS samples. */
public final class RouteMetrics {
  private static final double EARTH_RADIUS_METERS = 6_371_000;
  private static final double MIN_DISTANCE_METERS = 3;
  private static final double MAX_SPEED_METERS_PER_SECOND = 15;
  private static final long MAX_GAP_MS = 120_000;

  private RouteMetrics() {}

  public record Sample(
      double latitude,
      double longitude,
      long timeMs,
      int type,
      Integer heartRate,
      Double altitudeMeters) {}

  public record Segment(
      Sample start,
      Sample end,
      double distanceMeters,
      double paceSecondsPerKm,
      double speedKmPerHour,
      Integer heartRate,
      Double altitudeMeters) {}

  public static List<Segment> analyze(List<Sample> samples) {
    List<Segment> segments = new ArrayList<>();
    Sample previous = null;
    for (Sample sample : samples) {
      if (sample.type() != DB.LOCATION.TYPE_GPS || !validCoordinate(sample)) {
        previous = null;
        continue;
      }
      if (previous == null) {
        previous = sample;
        continue;
      }
      long durationMs = sample.timeMs() - previous.timeMs();
      if (durationMs <= 0 || durationMs > MAX_GAP_MS) {
        previous = sample;
        continue;
      }
      double distance = distanceMeters(previous, sample);
      double speed = distance * 1000 / durationMs;
      if (distance < MIN_DISTANCE_METERS || speed > MAX_SPEED_METERS_PER_SECOND) {
        previous = sample;
        continue;
      }
      Double altitude = validAltitude(previous.altitudeMeters()) && validAltitude(sample.altitudeMeters())
          ? (previous.altitudeMeters() + sample.altitudeMeters()) / 2 : null;
      Integer heartRate = sample.heartRate() != null && sample.heartRate() >= 30
          && sample.heartRate() <= 240 ? sample.heartRate() : null;
      segments.add(new Segment(previous, sample, distance, durationMs / distance,
          speed * 3.6, heartRate, altitude));
      previous = sample;
    }
    return segments;
  }

  private static boolean validCoordinate(Sample sample) {
    return Double.isFinite(sample.latitude()) && Double.isFinite(sample.longitude())
        && Math.abs(sample.latitude()) <= 90 && Math.abs(sample.longitude()) <= 180;
  }

  private static boolean validAltitude(Double altitude) {
    return altitude != null && Double.isFinite(altitude);
  }

  private static double distanceMeters(Sample a, Sample b) {
    double lat = Math.toRadians(b.latitude() - a.latitude());
    double lon = Math.toRadians(b.longitude() - a.longitude());
    double h = Math.pow(Math.sin(lat / 2), 2)
        + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
        * Math.pow(Math.sin(lon / 2), 2);
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
  }
}
