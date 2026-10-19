package org.runnerup.relven.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.runnerup.workout.RelvenPlanImporter
import org.runnerup.workout.RelvenPlanStore

/** Local plan library. The activity picker and calendar will consume these saved plans. */
class RelvenPlanLibraryActivity : ComponentActivity() {
  private var plans by mutableStateOf<List<RelvenPlanImporter.Plan>>(emptyList())
  private var selectedId by mutableStateOf<String?>(null)
  private var selectedMonth by mutableStateOf("")
  private var selectedDate by mutableStateOf("")
  private var showFormat by mutableStateOf(false)
  private var pendingPlan by mutableStateOf<RelvenPlanImporter.Plan?>(null)
  private var busy by mutableStateOf(false)
  private var message by mutableStateOf<String?>(null)

  private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) importPlan(uri)
  }
  private val kitDestination =
      registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) exportKit(uri)
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RelvenTheme {
        PlanLibrary(
            plans = plans,
            selectedId = selectedId,
            selectedMonth = selectedMonth,
            selectedDate = selectedDate,
            showFormat = showFormat,
            pendingPlan = pendingPlan,
            busy = busy,
            message = message,
            onBack = ::finish,
            onImport = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onExport = {
              kitDestination.launch("relven-ai-plan-kit-v1.zip")
            },
            onFormat = { showFormat = !showFormat },
            onConfirmImport = ::confirmImport,
            onCancelImport = { pendingPlan = null; message = null },
            onSelect = { id ->
              selectedId = if (selectedId == id) null else id
              plans.firstOrNull { it.id() == selectedId }?.days()?.minOfOrNull { it.date() }
                  ?.let { date ->
                    selectedDate = date
                    selectedMonth = date.substring(0, 7)
                  }
            },
            onMonthChange = { month ->
              selectedMonth = month
              selectedDate = plans.firstOrNull { it.id() == selectedId }?.days()
                  ?.filter { it.date().startsWith(month) }?.minOfOrNull { it.date() }
                  ?: "$month-01"
            },
            onDateChange = { selectedDate = it })
      }
    }
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      try {
        plans = withContext(Dispatchers.IO) { RelvenPlanStore.list(applicationContext) }
      } catch (error: IOException) {
        message = "Saved plans could not be read: ${error.message}"
      }
    }
  }

  private fun importPlan(uri: Uri) {
    if (busy) return
    busy = true
    message = null
    lifecycleScope.launch {
      try {
        val candidate = withContext(Dispatchers.IO) {
          val raw = readDocument(uri)
          RelvenPlanImporter.parse(raw)
        }
        pendingPlan = candidate
        message = "Review the plan before importing"
      } catch (error: Exception) {
        pendingPlan = null
        message = "Plan was not imported: ${error.message ?: "invalid file"}"
      } finally {
        busy = false
      }
    }
  }

  private fun confirmImport() {
    val candidate = pendingPlan ?: return
    if (busy) return
    busy = true
    message = null
    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) { RelvenPlanStore.save(applicationContext, candidate) }
        plans = withContext(Dispatchers.IO) { RelvenPlanStore.list(applicationContext) }
        selectedId = candidate.id()
        selectedDate = candidate.days().minOf { it.date() }
        selectedMonth = selectedDate.substring(0, 7)
        pendingPlan = null
        message = "Imported ${candidate.name()} · ${candidate.workoutDays()} workouts"
      } catch (error: Exception) {
        message = "Plan was not imported: ${error.message ?: "save failed"}"
      } finally {
        busy = false
      }
    }
  }

  private fun readDocument(uri: Uri): String {
    val input = contentResolver.openInputStream(uri) ?: throw IOException("Unable to open file")
    input.use { stream ->
      val output = ByteArrayOutputStream()
      val buffer = ByteArray(8192)
      while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        if (output.size() + count > 4_194_304) throw IOException("Plan exceeds 4 MB")
        output.write(buffer, 0, count)
      }
      return output.toString(Charsets.UTF_8.name())
    }
  }

  private fun exportKit(uri: Uri) {
    if (busy) return
    busy = true
    message = null
    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) {
          val output = contentResolver.openOutputStream(uri)
              ?: throw IOException("Unable to create file")
          assets.open("relven/relven-ai-plan-kit-v1.zip").use { input ->
            output.use { input.copyTo(it) }
          }
        }
        message = "AI Plan Kit exported"
      } catch (error: Exception) {
        message = "AI Plan Kit could not be exported: ${error.message}"
      } finally {
        busy = false
      }
    }
  }
}

@Composable
private fun PlanLibrary(
    plans: List<RelvenPlanImporter.Plan>,
    selectedId: String?,
    selectedMonth: String,
    selectedDate: String,
    showFormat: Boolean,
    pendingPlan: RelvenPlanImporter.Plan?,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onFormat: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onSelect: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onDateChange: (String) -> Unit) {
  val selected = plans.firstOrNull { it.id() == selectedId }
  Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Button(onClick = onImport, enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
          Text(if (busy) "Importing…" else "Import plan")
        }
      }
      Text("Training plans", fontSize = 28.sp, fontWeight = FontWeight.Bold)
      Text("Saved on this device", color = RelvenColors.MutedText)
      Text("AI plans", fontSize = 20.sp, fontWeight = FontWeight.Bold)
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
          Text("Export instructions")
        }
        OutlinedButton(onClick = onFormat, modifier = Modifier.fillMaxWidth()) {
          Text("Format support")
        }
      }
      if (showFormat) {
        Surface(color = RelvenColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
          Text("Imports local Relven plan v1 JSON with running workouts and rest days. " +
              "Time, distance and manual steps are supported. Strength plans, FIT workouts " +
              "and plan ZIP files are not supported yet.",
              modifier = Modifier.padding(14.dp))
        }
      }
      if (message != null) {
        Surface(color = RelvenColors.SurfaceRaised, modifier = Modifier.fillMaxWidth()) {
          Text(message, modifier = Modifier.padding(14.dp))
        }
      }
      if (pendingPlan != null) {
        Surface(color = RelvenColors.Surface, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Import preview", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(pendingPlan.name(), fontWeight = FontWeight.Bold)
            Text("${pendingPlan.workoutDays()} running sessions · " +
                "${pendingPlan.restDays()} rest days")
            Text("${pendingPlan.days().minOf { it.date() }} – " +
                pendingPlan.days().maxOf { it.date() })
            Text("Timezone: ${pendingPlan.timezone()}", color = RelvenColors.MutedText)
            Button(onClick = onConfirmImport, enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
              Text(if (busy) "Importing…" else "Import this plan")
            }
            OutlinedButton(onClick = onCancelImport, enabled = !busy) { Text("Cancel") }
          }
        }
      }
      if (plans.isEmpty()) {
        Text("No plans yet. Import a Relven plan JSON file to see its scheduled sessions.",
            color = RelvenColors.MutedText)
      } else {
        LazyColumn(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(plans, key = { it.id() }) { plan ->
            OutlinedButton(onClick = { onSelect(plan.id()) }, modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.fillMaxWidth()) {
                Text(plan.name(), fontWeight = FontWeight.Bold)
                Text("${plan.workoutDays()} workouts · ${plan.restDays()} rest days",
                    color = RelvenColors.MutedText)
              }
            }
          }
          if (selected != null) {
            item {
              CalendarMonth(selected, selectedMonth, selectedDate, onMonthChange, onDateChange)
            }
            val days = selected.days().filter { it.date() == selectedDate }
            if (days.isEmpty()) item {
              Text("No session scheduled for this date", color = RelvenColors.MutedText)
            }
            items(days, key = { it.id() }) { day ->
              Surface(
                  color = RelvenColors.Surface,
                  border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                  modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(day.date(), fontWeight = FontWeight.Bold)
                  Text(if (day.kind() == "rest") "Rest day"
                      else selected.workoutNames()[day.workoutId()] ?: "Running workout",
                      color = RelvenColors.MutedText)
                  if (day.notes().isNotBlank()) Text(day.notes())
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CalendarMonth(
    plan: RelvenPlanImporter.Plan,
    month: String,
    selectedDate: String,
    onMonthChange: (String) -> Unit,
    onDateChange: (String) -> Unit) {
  if (!month.matches(Regex("\\d{4}-\\d{2}"))) return
  val calendar = Calendar.getInstance(TimeZone.getTimeZone(plan.timezone()), Locale.ROOT)
  calendar.isLenient = false
  calendar.set(month.substring(0, 4).toInt(), month.substring(5, 7).toInt() - 1, 1)
  val offset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
  val dayCount = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
  val rows = (offset + dayCount + 6) / 7
  val dates = plan.days().map { it.date() }.toSet()
  val monthTitle = SimpleDateFormat("MMMM yyyy", LocalConfiguration.current.locales[0])
      .format(calendar.time)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      OutlinedButton(onClick = { onMonthChange(shiftMonth(month, -1)) }) { Text("‹") }
      Text(monthTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 10.dp))
      OutlinedButton(onClick = { onMonthChange(shiftMonth(month, 1)) }) { Text("›") }
    }
    Row(Modifier.fillMaxWidth()) {
      for (label in listOf("M", "T", "W", "T", "F", "S", "S")) {
        Text(label, modifier = Modifier.weight(1f), color = RelvenColors.MutedText)
      }
    }
    for (week in 0 until rows) {
      Row(Modifier.fillMaxWidth()) {
        for (weekday in 0 until 7) {
          val day = week * 7 + weekday - offset + 1
          if (day !in 1..dayCount) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
          } else {
            val date = "%s-%02d".format(Locale.ROOT, month, day)
            Surface(
                color = if (date == selectedDate) RelvenColors.SurfaceRaised else RelvenColors.Surface,
                modifier = Modifier.weight(1f).padding(2.dp).height(50.dp)
                    .semantics { contentDescription =
                        if (date in dates) "$date, scheduled session" else date }
                    .clickable { onDateChange(date) }) {
              Column(Modifier.padding(6.dp)) {
                Text(day.toString(), fontSize = 14.sp)
                if (date in dates) Text("•", color = RelvenColors.Orange, fontSize = 14.sp)
              }
            }
          }
        }
      }
    }
    Text(selectedDate, color = RelvenColors.MutedText)
  }
}

private fun shiftMonth(month: String, delta: Int): String {
  val calendar = Calendar.getInstance()
  calendar.set(month.substring(0, 4).toInt(), month.substring(5, 7).toInt() - 1, 1)
  calendar.add(Calendar.MONTH, delta)
  return "%04d-%02d".format(Locale.ROOT,
      calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}
