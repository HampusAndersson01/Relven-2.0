package org.runnerup.relven.ui

import android.content.ComponentName
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import org.runnerup.common.tracker.TrackerState
import org.runnerup.relven.ui.WearConnectionProvider.PreparationStatus
import org.runnerup.tracker.Tracker
import org.runnerup.view.RunActivity
import org.runnerup.common.util.Constants

internal class WorkoutPreparationActivity : ComponentActivity() {
  private lateinit var state: WorkoutPreparationStateHolder
  private var tracker: Tracker? = null
  private var bound = false
  private var trackerReady by mutableStateOf(false)
  private var selectedWorkoutAvailable by mutableStateOf(true)
  private var transferStarted = false
  private var restoreSelectedWorkout = false
  private var trackerStartIssued = false
  private var sessionCommandReceiverRegistered = false
  private val sessionCommandReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val payload = intent.getByteArrayExtra(Constants.Intents.EXTRA_WORKOUT_SESSION_COMMAND) ?: return
          val command =
              try {
                org.runnerup.common.wear.WorkoutSessionProtocol.decodeCommand(payload)
              } catch (_: java.io.IOException) {
                return
              }
          if (!isScheduledWatchStartForSession(command, state.value.sessionId)) return
          if (tracker?.state != TrackerState.CONNECTED || !selectedWorkoutAvailable) return
          val activeTracker = tracker ?: return
          val applied = activeTracker.applyWearSessionCommand(command)
          if (applied == org.runnerup.common.wear.WorkoutSessionProtocol.Result.ACCEPTED ||
              applied == org.runnerup.common.wear.WorkoutSessionProtocol.Result.DUPLICATE) {
            if (!state.acceptAuthoritativeStart(command)) return
            countdownHandler.removeCallbacks(countdownTick)
            countdownHandler.post(countdownTick)
          }
        }
      }
  private val countdownHandler = Handler(Looper.getMainLooper())
  private val countdownTick = object : Runnable {
    override fun run() {
      if (isFinishing || isDestroyed || state.value.phase != WorkoutPreparationPhase.COUNTDOWN) return
      val now = SystemClock.elapsedRealtime()
      state.updateCountdown(now)
      if (!state.isStartDue(now)) {
        countdownHandler.postDelayed(this, COUNTDOWN_REFRESH_MS)
        return
      }
      if (trackerStartIssued) return
      val activeTracker = tracker
      if (activeTracker == null || activeTracker.state != TrackerState.CONNECTED) {
        cancelCountdown("Anslutningen till passmotorn försvann")
        return
      }
      trackerStartIssued = true
      try {
        activeTracker.start()
        if (!state.completeStart(SystemClock.elapsedRealtime())) {
          // Tracker started only after the absolute deadline; state transition is still monotonic.
          state.completeStart(state.value.plannedStartElapsedRealtimeMs)
        }
      } catch (error: RuntimeException) {
        trackerStartIssued = false
        cancelCountdown("Kunde inte starta passet")
        return
      }
      WearConnectionProvider.cancelWorkoutPreparation(this@WorkoutPreparationActivity, state.value.sessionId)
      runActivityLauncher.launch(Intent(this@WorkoutPreparationActivity, RunActivity::class.java))
    }
  }

  private val runActivityLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        WearConnectionProvider.cancelWorkoutPreparation(this, state.value.sessionId)
        setResult(result.resultCode, result.data)
        finish()
      }

  private val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          tracker = (service as? Tracker.LocalBinder)?.service
          tracker?.let { activeTracker ->
            if (restoreSelectedWorkout) {
              restoreSelectedWorkout = false
              selectedWorkoutAvailable = restoreSelectedWorkoutFromIntent(activeTracker)
            }
            if (WorkoutPreparationTrackerPolicy.shouldConnect(activeTracker.state)) {
              activeTracker.connect()
            }
            if (state.value.phase != WorkoutPreparationPhase.PREPARING) {
              activeTracker.prepareWearWorkoutSession(
                  state.value.sessionId, state.value.watchReady)
            }
          }
          updateTrackerState()
          tracker?.registerTrackerStateListener(trackerStateListener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          tracker?.unregisterTrackerStateListener(trackerStateListener)
          tracker = null
          trackerReady = false
        }
      }

  private val trackerStateListener =
      object : org.runnerup.common.util.ValueModel.ChangeListener<TrackerState> {
        override fun onValueChanged(
            instance: org.runnerup.common.util.ValueModel<TrackerState>,
            oldValue: TrackerState,
            newValue: TrackerState
        ) {
          runOnUiThread(::updateTrackerState)
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
    val workoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME)
    val upcomingStep = intent.getStringExtra(EXTRA_UPCOMING_STEP)
    if (sessionId.isNullOrBlank() || workoutName.isNullOrBlank() || upcomingStep.isNullOrBlank()) {
      finish()
      return
    }
    state = WorkoutPreparationStateHolder(sessionId, workoutName, upcomingStep)
    restoreSelectedWorkout = savedInstanceState != null
    selectedWorkoutAvailable = !restoreSelectedWorkout
    setContent {
      RelvenTheme {
        WorkoutPreparationScreen(
            state = state.value,
            trackerReady = trackerReady,
            selectedWorkoutAvailable = selectedWorkoutAvailable,
            onStart = ::startWorkout,
            onCancel = ::cancelPreparation)
      }
    }
    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() = cancelPreparation()
        })
    bound = bindService(Intent(this, Tracker::class.java), connection, Context.BIND_AUTO_CREATE)
    ContextCompat.registerReceiver(
        this,
        sessionCommandReceiver,
        IntentFilter(Constants.Intents.WORKOUT_SESSION_COMMAND),
        ContextCompat.RECEIVER_NOT_EXPORTED)
    sessionCommandReceiverRegistered = true
    prepareWatch()
  }

  private fun prepareWatch() {
    if (transferStarted) return
    transferStarted = true
    val sessionId = state.value.sessionId
    val workoutJson = intent.getStringExtra(EXTRA_WORKOUT_JSON).orEmpty()
    WearConnectionProvider.prepareWorkout(
        this,
        sessionId,
        state.value.workoutName,
        state.value.upcomingStep,
        workoutJson) { result ->
          if (isFinishing || isDestroyed) return@prepareWorkout
          val ready = result.status == PreparationStatus.PREPARED
          val status =
              when (result.status) {
                PreparationStatus.PREPARED -> "Klockan redo"
                PreparationStatus.NO_WATCH -> "Ingen klocka ansluten · fortsätt på mobilen"
                PreparationStatus.TIMEOUT -> "Klockan svarar inte · fortsätt på mobilen"
                PreparationStatus.REJECTED -> "Klockan kunde inte läsa passet · fortsätt på mobilen"
                PreparationStatus.PROTOCOL_MISMATCH ->
                  "Uppdatera Relven på klockan · fortsätt på mobilen"
                PreparationStatus.UNSUPPORTED -> "Klockstöd saknas · fortsätt på mobilen"
                PreparationStatus.TRANSPORT_ERROR ->
                  "Klockan kunde inte nås · fortsätt på mobilen"
              }
          state.onWatchPreparationFinished(ready, status)
          tracker?.prepareWearWorkoutSession(sessionId, ready)
        }
  }

  private fun updateTrackerState() {
    trackerReady = tracker?.state == TrackerState.CONNECTED && selectedWorkoutAvailable
  }

  private fun startWorkout() {
    val activeTracker = tracker ?: return
    if (activeTracker.state != TrackerState.CONNECTED || !selectedWorkoutAvailable) return
    activeTracker.prepareWearWorkoutSession(state.value.sessionId, state.value.watchReady)
    val command = state.createStartCommand(SystemClock.elapsedRealtime()) ?: return
    val result = activeTracker.applyWearSessionCommand(command)
    if (result == org.runnerup.common.wear.WorkoutSessionProtocol.Result.REJECTED) return
    if (!state.acceptAuthoritativeStart(command)) return
    countdownHandler.removeCallbacks(countdownTick)
    countdownHandler.post(countdownTick)
  }

  private fun cancelPreparation() {
    cancelCountdown("Passförberedelsen avbröts")
    WearConnectionProvider.cancelWorkoutPreparation(this, state.value.sessionId)
    finish()
  }

  private fun cancelCountdown(status: String) {
    val command = state.cancelCountdown(status) ?: return
    tracker?.applyWearSessionCommand(command)
  }

  private fun restoreSelectedWorkoutFromIntent(activeTracker: Tracker): Boolean {
    return try {
      val restored =
          WorkoutPreparationWorkoutRestorer.restore(
              this,
              intent.getStringExtra(EXTRA_WORKOUT_JSON).orEmpty(),
              intent.getIntExtra(EXTRA_SPORT, Constants.DB.ACTIVITY.SPORT_RUNNING),
              intent.getIntExtra(EXTRA_WORKOUT_TYPE, Constants.WORKOUT_TYPE.BASIC))
      activeTracker.setWorkout(restored)
      true
    } catch (error: Exception) {
      android.util.Log.e("WorkoutPreparation", "Unable to restore selected workout", error)
      false
    }
  }

  override fun onDestroy() {
    countdownHandler.removeCallbacksAndMessages(null)
    if (sessionCommandReceiverRegistered) {
      unregisterReceiver(sessionCommandReceiver)
      sessionCommandReceiverRegistered = false
    }
    tracker?.unregisterTrackerStateListener(trackerStateListener)
    if (bound) {
      unbindService(connection)
      bound = false
    }
    super.onDestroy()
  }

  companion object {
    private const val COUNTDOWN_REFRESH_MS = 50L
    private const val EXTRA_SESSION_ID = "org.runnerup.relven.preparation.session_id"
    private const val EXTRA_WORKOUT_NAME = "org.runnerup.relven.preparation.workout_name"
    private const val EXTRA_UPCOMING_STEP = "org.runnerup.relven.preparation.upcoming_step"
    private const val EXTRA_WORKOUT_JSON = "org.runnerup.relven.preparation.workout_json"
    private const val EXTRA_SPORT = "org.runnerup.relven.preparation.sport"
    private const val EXTRA_WORKOUT_TYPE = "org.runnerup.relven.preparation.workout_type"

    @JvmStatic
    fun intent(
        context: Context,
        sessionId: String,
        workoutName: String,
        upcomingStep: String,
        workoutJson: String,
        sport: Int,
        workoutType: Int
    ): Intent =
        Intent(context, WorkoutPreparationActivity::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_WORKOUT_NAME, workoutName)
            .putExtra(EXTRA_UPCOMING_STEP, upcomingStep)
            .putExtra(EXTRA_WORKOUT_JSON, workoutJson)
            .putExtra(EXTRA_SPORT, sport)
            .putExtra(EXTRA_WORKOUT_TYPE, workoutType)
  }
}
