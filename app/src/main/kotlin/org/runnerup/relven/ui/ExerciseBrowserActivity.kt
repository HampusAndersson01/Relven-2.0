package org.runnerup.relven.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.runnerup.relven.exercise.ExerciseCatalog

/** Browses the bundled offline exercise metadata without loading remote media. */
class ExerciseBrowserActivity : ComponentActivity() {
  private var exercises by mutableStateOf<List<ExerciseCatalog.Exercise>>(emptyList())
  private var query by mutableStateOf("")
  private var favoritesOnly by mutableStateOf(false)
  private var category by mutableStateOf<String?>(null)
  private var equipment by mutableStateOf<String?>(null)
  private var muscle by mutableStateOf<String?>(null)
  private var filters by mutableStateOf(
      ExerciseCatalog.FilterOptions(emptyList(), emptyList(), emptyList()))
  private var selected by mutableStateOf<ExerciseCatalog.Exercise?>(null)
  private var editing by mutableStateOf(false)
  private var editorId by mutableStateOf<String?>(null)
  private var customName by mutableStateOf("")
  private var customInstructions by mutableStateOf("")
  private var loading by mutableStateOf(false)
  private var message by mutableStateOf<String?>(null)
  private var searchJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RelvenTheme {
        ExerciseBrowser(
            exercises = exercises,
            query = query,
            favoritesOnly = favoritesOnly,
            category = category,
            equipment = equipment,
            muscle = muscle,
            filters = filters,
            selected = selected,
            editing = editing,
            customName = customName,
            customInstructions = customInstructions,
            loading = loading,
            message = message,
            onBack = {
              if (editing) editing = false
              else if (selected != null) selected = null
              else finish()
            },
            onQuery = { query = it; search() },
            onFavorites = { favoritesOnly = !favoritesOnly; search() },
            onCategory = { category = it; search() },
            onEquipment = { equipment = it; search() },
            onMuscle = { muscle = it; search() },
            onSelect = { selected = it },
            onFavorite = { exercise -> toggleFavorite(exercise) },
            onNew = { beginEdit(null) },
            onEdit = { beginEdit(it) },
            onName = { customName = it },
            onInstructions = { customInstructions = it },
            onSave = ::saveCustom)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      try {
        filters = withContext(Dispatchers.IO) {
          val catalog = ExerciseCatalog(applicationContext)
          try { catalog.filterOptions() } finally { catalog.close() }
        }
        search()
      } catch (error: Exception) {
        message = "Exercise filters could not be loaded: ${error.message}"
      }
    }
  }

  private fun search() {
    searchJob?.cancel()
    searchJob = lifecycleScope.launch {
      loading = true
      message = null
      try {
        val result = withContext(Dispatchers.IO) {
          val catalog = ExerciseCatalog(applicationContext)
          try { catalog.search(query, category, equipment, muscle, 200, favoritesOnly) }
          finally { catalog.close() }
        }
        exercises = result
      } catch (error: Exception) {
        message = "Exercises could not be loaded: ${error.message}"
      } finally {
        loading = false
      }
    }
  }

  private fun toggleFavorite(exercise: ExerciseCatalog.Exercise) {
    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) {
          val catalog = ExerciseCatalog(applicationContext)
          try { catalog.setFavorite(exercise.id(), !exercise.favorite()) }
          finally { catalog.close() }
        }
        selected = selected?.let {
          if (it.id() == exercise.id()) it.copyFavorite(!exercise.favorite()) else it
        }
        search()
      } catch (error: Exception) {
        message = "Favorite could not be saved: ${error.message}"
      }
    }
  }

  private fun beginEdit(exercise: ExerciseCatalog.Exercise?) {
    editorId = exercise?.id()
    customName = exercise?.name() ?: ""
    customInstructions = exercise?.instructionsJson()?.let { raw ->
      val items = JSONArray(raw)
      (0 until items.length()).joinToString("\n") { items.optString(it) }
    } ?: ""
    editing = true
    message = null
  }

  private fun saveCustom() {
    if (loading) return
    loading = true
    message = null
    lifecycleScope.launch {
      try {
        val id = withContext(Dispatchers.IO) {
          val catalog = ExerciseCatalog(applicationContext)
          try {
            val instructions = customInstructions.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val existingId = editorId
            if (existingId == null) catalog.createCustom(customName, instructions)
            else {
              catalog.updateCustom(existingId, customName, instructions)
              existingId
            }
          } finally { catalog.close() }
        }
        editing = false
        val catalog = ExerciseCatalog(applicationContext)
        selected = try { withContext(Dispatchers.IO) { catalog.find(id) } }
            finally { catalog.close() }
        message = "Exercise saved"
        search()
      } catch (error: Exception) {
        message = "Exercise was not saved: ${error.message}"
      } finally {
        loading = false
      }
    }
  }
}

private fun ExerciseCatalog.Exercise.copyFavorite(favorite: Boolean) =
    ExerciseCatalog.Exercise(id(), sourceId(), name(), instructionsJson(), category(),
        equipment(), primaryMusclesJson(), secondaryMusclesJson(), movementType(),
        difficulty(), source(), revision(), custom(), favorite)

@Composable
private fun ExerciseBrowser(
    exercises: List<ExerciseCatalog.Exercise>,
    query: String,
    favoritesOnly: Boolean,
    category: String?,
    equipment: String?,
    muscle: String?,
    filters: ExerciseCatalog.FilterOptions,
    selected: ExerciseCatalog.Exercise?,
    editing: Boolean,
    customName: String,
    customInstructions: String,
    loading: Boolean,
    message: String?,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onFavorites: () -> Unit,
    onCategory: (String?) -> Unit,
    onEquipment: (String?) -> Unit,
    onMuscle: (String?) -> Unit,
    onSelect: (ExerciseCatalog.Exercise) -> Unit,
    onFavorite: (ExerciseCatalog.Exercise) -> Unit,
    onNew: () -> Unit,
    onEdit: (ExerciseCatalog.Exercise) -> Unit,
    onName: (String) -> Unit,
    onInstructions: (String) -> Unit,
    onSave: () -> Unit) {
  Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      OutlinedButton(onClick = onBack) { Text("Back") }
      Text(if (editing) "Custom exercise" else selected?.name() ?: "Exercises",
          fontSize = 27.sp, fontWeight = FontWeight.Bold)
      if (message != null) Text(message, color = RelvenColors.Orange)
      if (editing) {
        OutlinedTextField(value = customName, onValueChange = onName,
            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = customInstructions, onValueChange = onInstructions,
            label = { Text("Instructions, one step per line") },
            minLines = 5, modifier = Modifier.fillMaxWidth())
        Button(onClick = onSave, enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
          Text(if (loading) "Saving…" else "Save exercise")
        }
      } else if (selected == null) {
        Button(onClick = onNew,
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
          Text("Add custom exercise")
        }
        OutlinedTextField(value = query, onValueChange = onQuery,
            label = { Text("Search exercises") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ExerciseFilter("Category", category, filters.categories(), onCategory)
          ExerciseFilter("Equipment", equipment, filters.equipment(), onEquipment)
          ExerciseFilter("Muscle", muscle, filters.muscles(), onMuscle)
        }
        OutlinedButton(onClick = onFavorites) {
          Text(if (favoritesOnly) "Show all" else "Favorites only")
        }
        if (loading) Text("Loading exercises…", color = RelvenColors.MutedText)
        if (!loading && exercises.isEmpty()) {
          Text("No exercises found", color = RelvenColors.MutedText)
        }
        LazyColumn(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(exercises, key = { it.id() }) { exercise ->
            OutlinedButton(onClick = { onSelect(exercise) }, modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.fillMaxWidth()) {
                Text(exercise.name(), fontWeight = FontWeight.Bold)
                Text(listOfNotNull(exercise.category(), exercise.equipment()).joinToString(" · "),
                    color = RelvenColors.MutedText)
              }
            }
          }
        }
      } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(listOfNotNull(selected.category(), selected.equipment()).joinToString(" · "),
              color = RelvenColors.MutedText, modifier = Modifier.weight(1f))
          Button(onClick = { onFavorite(selected) },
              colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
            Text(if (selected.favorite()) "Favorited" else "Favorite")
          }
        }
        if (selected.custom()) {
          OutlinedButton(onClick = { onEdit(selected) }) { Text("Edit exercise") }
        }
        val instructions = JSONArray(selected.instructionsJson())
        LazyColumn(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(instructions.length()) { index ->
            Surface(color = RelvenColors.Surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()) {
              Text("${index + 1}. ${instructions.optString(index)}",
                  modifier = Modifier.padding(16.dp))
            }
          }
          item { Text("Source: ${selected.source()}", color = RelvenColors.MutedText) }
        }
      }
    }
  }
}

@Composable
private fun ExerciseFilter(
    label: String,
    selected: String?,
    options: List<String>,
    onSelect: (String?) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Column {
    OutlinedButton(onClick = { open = true }) { Text(selected ?: label) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(text = { Text("All $label") },
          onClick = { onSelect(null); open = false })
      for (option in options) {
        DropdownMenuItem(text = { Text(option) },
            onClick = { onSelect(option); open = false })
      }
    }
  }
}
