package org.runnerup.relven.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsPreparationPresentationTest {
  @Test
  fun runningWorkoutCanStartOnlyAfterUsableGpsFix() {
    assertEquals(false, state(GpsPreparationStatus.PERMISSION_REQUIRED).canStartWorkout)
    assertEquals(false, state(GpsPreparationStatus.UNAVAILABLE).canStartWorkout)
    assertEquals(false, state(GpsPreparationStatus.DISABLED).canStartWorkout)
    assertEquals(false, state(GpsPreparationStatus.SEARCHING).canStartWorkout)
    assertEquals(false, state(GpsPreparationStatus.FIX_ACQUIRED).canStartWorkout)
    assertEquals(false, state(GpsPreparationStatus.ACCURACY_INSUFFICIENT).canStartWorkout)
    assertEquals(true, state(GpsPreparationStatus.READY).canStartWorkout)
    assertEquals(true, state(GpsPreparationStatus.NOT_REQUIRED).canStartWorkout)
  }

  @Test
  fun gpsFreeWorkoutDoesNotShowGpsReadiness() {
    val state =
      GpsPreparationPresentation.create(
        required = false,
        permissionGranted = false,
        providerAvailable = false,
        providerEnabled = false,
        updatesActive = false,
        locationReceived = false,
        accuracyMeters = null,
      )

    assertEquals(GpsPreparationStatus.NOT_REQUIRED, state.status)
    assertNull(state.accuracyMeters)
  }

  @Test
  fun permissionProviderAndSearchingStatesAreDistinct() {
    assertStatus(
      GpsPreparationStatus.PERMISSION_REQUIRED,
      permissionGranted = false,
      providerAvailable = true,
      providerEnabled = true,
      updatesActive = true,
      locationReceived = true,
      accuracyMeters = 4f,
    )
    assertStatus(
      GpsPreparationStatus.UNAVAILABLE,
      providerAvailable = false,
      providerEnabled = true,
      updatesActive = true,
    )
    assertStatus(
      GpsPreparationStatus.DISABLED,
      providerEnabled = false,
      updatesActive = true,
    )
    assertStatus(GpsPreparationStatus.UNAVAILABLE, updatesActive = false)
    assertStatus(GpsPreparationStatus.SEARCHING, updatesActive = true)
  }

  @Test
  fun fixAndAccuracyAreReportedWithoutInventingAccuracy() {
    val fixWithoutAccuracy =
      GpsPreparationPresentation.create(
        required = true,
        permissionGranted = true,
        providerAvailable = true,
        providerEnabled = true,
        updatesActive = true,
        locationReceived = true,
        accuracyMeters = null,
      )
    assertEquals(GpsPreparationStatus.FIX_ACQUIRED, fixWithoutAccuracy.status)
    assertNull(fixWithoutAccuracy.accuracyMeters)

    val inaccurate = presentation(accuracyMeters = 25f)
    assertEquals(GpsPreparationStatus.ACCURACY_INSUFFICIENT, inaccurate.status)
    assertEquals(25f, inaccurate.accuracyMeters)

    val ready = presentation(accuracyMeters = 9f)
    assertEquals(GpsPreparationStatus.READY, ready.status)
    assertEquals(9f, ready.accuracyMeters)
  }

  private fun assertStatus(
    expected: GpsPreparationStatus,
    permissionGranted: Boolean = true,
    providerAvailable: Boolean = true,
    providerEnabled: Boolean = true,
    updatesActive: Boolean = true,
    locationReceived: Boolean = false,
    accuracyMeters: Float? = null,
  ) {
    assertEquals(
      expected,
      GpsPreparationPresentation.create(
          required = true,
          permissionGranted = permissionGranted,
          providerAvailable = providerAvailable,
          providerEnabled = providerEnabled,
          updatesActive = updatesActive,
          locationReceived = locationReceived,
          accuracyMeters = accuracyMeters,
        )
        .status,
    )
  }

  private fun presentation(accuracyMeters: Float?) =
    GpsPreparationPresentation.create(
      required = true,
      permissionGranted = true,
      providerAvailable = true,
      providerEnabled = true,
      updatesActive = true,
      locationReceived = true,
      accuracyMeters = accuracyMeters,
    )

  private fun state(status: GpsPreparationStatus) = GpsPreparationUiState(status)
}
