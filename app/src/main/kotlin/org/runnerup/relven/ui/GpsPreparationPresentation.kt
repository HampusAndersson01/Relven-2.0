package org.runnerup.relven.ui

internal enum class GpsPreparationStatus {
  NOT_REQUIRED,
  PERMISSION_REQUIRED,
  UNAVAILABLE,
  DISABLED,
  SEARCHING,
  FIX_ACQUIRED,
  ACCURACY_INSUFFICIENT,
  READY,
}

internal data class GpsPreparationUiState(
  val status: GpsPreparationStatus,
  val accuracyMeters: Float? = null,
) {
  /** Running can begin only after a usable GPS fix; non-GPS workouts are unaffected. */
  val canStartWorkout: Boolean
    get() = status == GpsPreparationStatus.NOT_REQUIRED || status == GpsPreparationStatus.READY

  val label: String
    get() =
      when (status) {
        GpsPreparationStatus.NOT_REQUIRED -> "GPS behövs inte"
        GpsPreparationStatus.PERMISSION_REQUIRED -> "Platsåtkomst krävs för att starta löppasset"
        GpsPreparationStatus.UNAVAILABLE -> "GPS är inte tillgängligt · kontrollera enheten"
        GpsPreparationStatus.DISABLED -> "Plats är avstängd · slå på plats för att starta"
        GpsPreparationStatus.SEARCHING -> "Söker GPS-signal · passet startar när GPS är redo"
        GpsPreparationStatus.FIX_ACQUIRED -> "GPS-position hittad · kontrollerar noggrannhet"
        GpsPreparationStatus.ACCURACY_INSUFFICIENT -> "GPS-signal svag · väntar på bättre noggrannhet"
        GpsPreparationStatus.READY -> "GPS redo"
      }
}

internal object GpsPreparationPresentation {
  private const val READY_ACCURACY_METERS = 10f

  fun create(
    required: Boolean,
    permissionGranted: Boolean,
    providerAvailable: Boolean,
    providerEnabled: Boolean,
    updatesActive: Boolean,
    locationReceived: Boolean,
    accuracyMeters: Float?,
  ): GpsPreparationUiState {
    if (!required) return GpsPreparationUiState(GpsPreparationStatus.NOT_REQUIRED)
    if (!permissionGranted) return GpsPreparationUiState(GpsPreparationStatus.PERMISSION_REQUIRED)
    if (!providerAvailable) return GpsPreparationUiState(GpsPreparationStatus.UNAVAILABLE)
    if (!providerEnabled) return GpsPreparationUiState(GpsPreparationStatus.DISABLED)
    if (!updatesActive) return GpsPreparationUiState(GpsPreparationStatus.UNAVAILABLE)
    if (!locationReceived) {
      return GpsPreparationUiState(GpsPreparationStatus.SEARCHING)
    }

    val measuredAccuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0f }
    if (measuredAccuracy == null) {
      return GpsPreparationUiState(GpsPreparationStatus.FIX_ACQUIRED)
    }
    return GpsPreparationUiState(
      if (measuredAccuracy <= READY_ACCURACY_METERS) GpsPreparationStatus.READY
      else GpsPreparationStatus.ACCURACY_INSUFFICIENT,
      measuredAccuracy,
    )
  }
}
