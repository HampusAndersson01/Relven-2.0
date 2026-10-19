/* Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 * Copyright (C) 2026 Relven contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.runnerup.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.relven.map.RouteMetrics;

public class MapWrapper {
  private static final String[] MODES = {"Route", "Pace", "Speed", "Heart rate", "Elevation"};
  private static final int[] RAMP = {
    Color.rgb(219, 77, 74), Color.rgb(226, 133, 63), Color.rgb(229, 184, 67),
    Color.rgb(172, 197, 80), Color.rgb(91, 190, 116), Color.rgb(38, 173, 150),
    Color.rgb(36, 149, 193)
  };
  private static final int ROUTE_COLOR = Color.rgb(41, 187, 169);
  private static final int MISSING_COLOR = Color.rgb(150, 155, 160);
  private final SQLiteDatabase database;
  private final long activityId;
  private final MapView mapView;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final List<Overlay> overlays = new ArrayList<>();
  private Spinner selector;
  private TextView legend;
  private RouteData route;

  public MapWrapper(
      Context context,
      SQLiteDatabase database,
      long activityId,
      Formatter formatter,
      Object mapView) {
    this.database = database;
    this.activityId = activityId;
    this.mapView = (MapView) mapView;
  }

  public static void start(Context context) {}

  public void onCreate(Bundle savedInstanceState) {
    Configuration.getInstance().setUserAgentValue("se.hampusandersson.relven");
    mapView.setTileSource(
        new XYTileSource(
            "OpenStreetMap", 0, 19, 256, ".png", new String[] {"https://tile.openstreetmap.org/"}));
    mapView
        .getZoomController()
        .setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
    mapView.setMultiTouchControls(true);
    selector = mapView.getRootView().findViewById(R.id.route_metric_selector);
    legend = mapView.getRootView().findViewById(R.id.route_metric_legend);
    mapView
        .getRootView()
        .findViewById(R.id.route_recenter)
        .setOnClickListener(
            view -> {
              if (route != null && route.first() != null)
                mapView.getController().setCenter(route.first());
            });
    mapView.getRootView().findViewById(R.id.route_fit).setOnClickListener(view -> fitRoute());
    selector.setAdapter(
        new ArrayAdapter<>(
            mapView.getContext(), android.R.layout.simple_spinner_dropdown_item, MODES));
    selector.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (route != null) render(position);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
    executor.execute(
        () -> {
          RouteData data = readRoute();
          mapView.post(
              () -> {
                route = data;
                if (data.first() != null) {
                  mapView.getController().setZoom(15.0);
                  mapView.getController().setCenter(data.first());
                }
                render(selector.getSelectedItemPosition());
              });
        });
  }

  private record LapPoint(GeoPoint point, int lap) {}

  private record RouteData(
      List<RouteMetrics.Segment> segments, List<LapPoint> laps, GeoPoint first, GeoPoint last) {}

  private RouteData readRoute() {
    List<RouteMetrics.Sample> samples = new ArrayList<>();
    List<LapPoint> laps = new ArrayList<>();
    GeoPoint first = null;
    GeoPoint last = null;
    int lastLap = -1;
    String[] columns = {
      DB.LOCATION.LATITUDE,
      DB.LOCATION.LONGITUDE,
      DB.LOCATION.TIME,
      DB.LOCATION.TYPE,
      DB.LOCATION.HR,
      DB.LOCATION.ALTITUDE,
      DB.LOCATION.LAP
    };
    try (Cursor cursor =
        database.query(
            DB.LOCATION.TABLE,
            columns,
            DB.LOCATION.ACTIVITY + "=?",
            new String[] {Long.toString(activityId)},
            null,
            null,
            "_id")) {
      while (cursor.moveToNext()) {
        if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(2) || cursor.isNull(3)) continue;
        double latitude = cursor.getDouble(0);
        double longitude = cursor.getDouble(1);
        int type = cursor.getInt(3);
        samples.add(
            new RouteMetrics.Sample(
                latitude,
                longitude,
                cursor.getLong(2),
                type,
                cursor.isNull(4) ? null : cursor.getInt(4),
                cursor.isNull(5) ? null : cursor.getDouble(5)));
        if (type != DB.LOCATION.TYPE_GPS
            || !Double.isFinite(latitude)
            || !Double.isFinite(longitude)
            || Math.abs(latitude) > 90
            || Math.abs(longitude) > 180) continue;
        GeoPoint point = new GeoPoint(latitude, longitude);
        if (first == null) first = point;
        last = point;
        if (!cursor.isNull(6) && cursor.getInt(6) != lastLap) {
          lastLap = cursor.getInt(6);
          laps.add(new LapPoint(point, lastLap));
        }
      }
    }
    return new RouteData(RouteMetrics.analyze(samples), laps, first, last);
  }

  private void render(int mode) {
    mapView.getOverlays().removeAll(overlays);
    overlays.clear();
    if (route == null) return;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (RouteMetrics.Segment segment : route.segments()) {
      Double value = metric(segment, mode);
      if (value != null) {
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
    }
    Polyline line = null;
    int previousColor = -1;
    RouteMetrics.Sample previousEnd = null;
    for (RouteMetrics.Segment segment : route.segments()) {
      Double value = metric(segment, mode);
      int color =
          mode == 0
              ? ROUTE_COLOR
              : value == null ? MISSING_COLOR : RAMP[bucket(value, min, max, mode == 1)];
      if (line == null || color != previousColor || previousEnd != segment.start()) {
        line = new Polyline(mapView, true);
        line.setInfoWindow(null);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setStrokeWidth(10f);
        line.addPoint(point(segment.start()));
        overlays.add(line);
        previousColor = color;
      }
      line.addPoint(point(segment.end()));
      previousEnd = segment.end();
    }
    for (LapPoint lap : route.laps()) overlays.add(marker(lap.point(), "Lap " + lap.lap()));
    if (route.first() != null) overlays.add(marker(route.first(), "Start"));
    if (route.last() != null && !route.last().equals(route.first()))
      overlays.add(marker(route.last(), "Finish"));
    mapView.getOverlays().addAll(overlays);
    legend.setText(
        route.first() == null
            ? mapView.getContext().getString(R.string.route_no_gps)
            : legendText(mode, min, max));
    mapView.invalidate();
  }

  private void fitRoute() {
    if (route == null || route.first() == null) return;
    double north = route.first().getLatitude();
    double south = north;
    double east = route.first().getLongitude();
    double west = east;
    for (RouteMetrics.Segment segment : route.segments()) {
      RouteMetrics.Sample end = segment.end();
      north = Math.max(north, end.latitude());
      south = Math.min(south, end.latitude());
      east = Math.max(east, end.longitude());
      west = Math.min(west, end.longitude());
    }
    if (north == south && east == west) {
      mapView.getController().setCenter(route.first());
    } else {
      mapView.zoomToBoundingBox(new BoundingBox(north, east, south, west), true, 48);
    }
  }

  private static int bucket(double value, double min, double max, boolean lowerIsBetter) {
    double position = max == min ? 0.5 : (value - min) / (max - min);
    if (lowerIsBetter) position = 1 - position;
    return Math.max(0, Math.min(RAMP.length - 1, (int) Math.floor(position * RAMP.length)));
  }

  private static Double metric(RouteMetrics.Segment segment, int mode) {
    return switch (mode) {
      case 1 -> segment.paceSecondsPerKm();
      case 2 -> segment.speedKmPerHour();
      case 3 -> segment.heartRate() == null ? null : segment.heartRate().doubleValue();
      case 4 -> segment.altitudeMeters();
      default -> null;
    };
  }

  private static String legendText(int mode, double min, double max) {
    if (mode == 0) return "Recorded route";
    if (!Double.isFinite(min)) return MODES[mode] + ": no recorded values";
    if (mode == 1) return "Fast " + pace(min) + " → Slow " + pace(max) + " min/km";
    String unit =
        switch (mode) {
          case 2 -> " km/h";
          case 3 -> " bpm";
          default -> " m";
        };
    return String.format(Locale.getDefault(), "%s: %.0f–%.0f%s", MODES[mode], min, max, unit);
  }

  private static String pace(double secondsPerKm) {
    long seconds = Math.round(secondsPerKm);
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
  }

  private static GeoPoint point(RouteMetrics.Sample sample) {
    return new GeoPoint(sample.latitude(), sample.longitude());
  }

  private Marker marker(GeoPoint point, String label) {
    Marker marker = new Marker(mapView);
    marker.setPosition(point);
    marker.setTextIcon(label);
    marker.setInfoWindow(null);
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
    return marker;
  }

  public void onResume() {
    mapView.onResume();
  }

  public void onStart() {}

  public void onStop() {}

  public void onPause() {
    mapView.onPause();
  }

  public void onSaveInstanceState(Bundle outState) {}

  public void onLowMemory() {}

  public void onDestroy() {
    executor.shutdown();
  }
}
