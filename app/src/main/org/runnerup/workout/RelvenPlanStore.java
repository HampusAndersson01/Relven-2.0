package org.runnerup.workout;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stores validated plans atomically without changing active workouts or activity history. */
public final class RelvenPlanStore {
  private static final int MAX_BYTES = 4_194_304;

  private RelvenPlanStore() {}

  public static void save(Context context, RelvenPlanImporter.Plan plan) throws IOException {
    RelvenPlanImporter.Plan verified = RelvenPlanImporter.parse(plan.sourceJson());
    if (!verified.id().equals(plan.id())) throw new IOException("Plan ID changed before saving");
    if (plan.sourceJson().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new IOException("Plan file is too large");
    }
    File directory = context.getDir("relven-plans", Context.MODE_PRIVATE);
    File destination = new File(directory, hashId(plan.id()) + ".json");
    if (destination.exists()) throw new IOException("Plan already imported: " + plan.name());

    AtomicFile file = new AtomicFile(destination);
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(plan.sourceJson().getBytes(StandardCharsets.UTF_8));
      file.finishWrite(stream);
    } catch (IOException error) {
      if (stream != null) file.failWrite(stream);
      throw error;
    }
  }

  /** Reads every saved plan and reports corrupt files instead of silently hiding them. */
  public static List<RelvenPlanImporter.Plan> list(Context context) throws IOException {
    File[] files =
        context
            .getDir("relven-plans", Context.MODE_PRIVATE)
            .listFiles((directory, name) -> name.endsWith(".json"));
    if (files == null) throw new IOException("Unable to read saved plans");
    List<RelvenPlanImporter.Plan> plans = new ArrayList<>();
    for (File file : files) {
      if (file.length() > MAX_BYTES) throw new IOException("Saved plan is too large");
      try (FileInputStream input = new FileInputStream(file);
          ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) != -1) {
          if (output.size() + length > MAX_BYTES) throw new IOException("Saved plan is too large");
          output.write(buffer, 0, length);
        }
        try {
          plans.add(RelvenPlanImporter.parse(output.toString(StandardCharsets.UTF_8.name())));
        } catch (IllegalArgumentException error) {
          throw new IOException("Saved plan could not be read", error);
        }
      }
    }
    plans.sort(Comparator.comparing(RelvenPlanImporter.Plan::name, String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(plans);
  }

  private static String hashId(String id) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        result.append(Character.forDigit((value >> 4) & 0xF, 16));
        result.append(Character.forDigit(value & 0xF, 16));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }
}
