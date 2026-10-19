package org.runnerup.relven.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;
import org.runnerup.common.util.Constants.DB;

public class RouteMetricsTest {
  @Test
  public void computesPaceAndSpeedFromMovingGpsSamples() {
    List<RouteMetrics.Segment> segments =
        RouteMetrics.analyze(
            List.of(
                new RouteMetrics.Sample(59.0, 18.0, 1000, DB.LOCATION.TYPE_GPS, 140, 20.0),
                new RouteMetrics.Sample(59.0009, 18.0, 31_000, DB.LOCATION.TYPE_GPS, 145, 22.0)));
    assertEquals(1, segments.size());
    assertEquals(100, segments.get(0).distanceMeters(), 2);
    assertEquals(300, segments.get(0).paceSecondsPerKm(), 6);
    assertEquals(12, segments.get(0).speedKmPerHour(), 0.3);
    assertEquals(145, segments.get(0).heartRate().intValue());
    assertEquals(21, segments.get(0).altitudeMeters(), 0.01);
  }

  @Test
  public void doesNotConnectAcrossPause() {
    List<RouteMetrics.Segment> segments =
        RouteMetrics.analyze(
            List.of(
                new RouteMetrics.Sample(59.0, 18.0, 1000, DB.LOCATION.TYPE_GPS, null, null),
                new RouteMetrics.Sample(59.0, 18.0, 2000, DB.LOCATION.TYPE_PAUSE, null, null),
                new RouteMetrics.Sample(59.001, 18.0, 3000, DB.LOCATION.TYPE_RESUME, null, null),
                new RouteMetrics.Sample(59.0019, 18.0, 33_000, DB.LOCATION.TYPE_GPS, null, null)));
    assertEquals(0, segments.size());
  }

  @Test
  public void rejectsGpsSpikeAndKeepsMissingMetricsMissing() {
    List<RouteMetrics.Segment> segments =
        RouteMetrics.analyze(
            List.of(
                new RouteMetrics.Sample(59.0, 18.0, 1000, DB.LOCATION.TYPE_GPS, null, null),
                new RouteMetrics.Sample(60.0, 18.0, 2000, DB.LOCATION.TYPE_GPS, 280, null),
                new RouteMetrics.Sample(60.0009, 18.0, 32_000, DB.LOCATION.TYPE_GPS, 0, null)));
    assertEquals(1, segments.size());
    assertNull(segments.get(0).heartRate());
    assertNull(segments.get(0).altitudeMeters());
  }

  @Test
  public void doesNotInventMovementAcrossLongGapOrBadCoordinates() {
    List<RouteMetrics.Segment> segments =
        RouteMetrics.analyze(
            List.of(
                new RouteMetrics.Sample(59.0, 18.0, 1_000, DB.LOCATION.TYPE_GPS, 140, 20.0),
                new RouteMetrics.Sample(59.0009, 18.0, 200_000, DB.LOCATION.TYPE_GPS, 145, 21.0),
                new RouteMetrics.Sample(95.0, 18.0, 210_000, DB.LOCATION.TYPE_GPS, 150, 22.0),
                new RouteMetrics.Sample(59.0018, 18.0, 220_000, DB.LOCATION.TYPE_GPS, 155, 23.0),
                new RouteMetrics.Sample(59.0027, 18.0, 250_000, DB.LOCATION.TYPE_GPS, 160, 24.0)));
    assertEquals(1, segments.size());
    assertEquals(160, segments.get(0).heartRate().intValue());
  }
}
