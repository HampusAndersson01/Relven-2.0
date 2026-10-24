package org.runnerup.relven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.relven.settings.RelvenSettingsSnapshot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RelvenSettingsOverviewTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun workoutPreferenceToggleWritesItsRequestedValue() {
    var requestedAutoPause: Boolean? = null
    composeRule.setContent {
      RelvenSettingsOverview(
          route = SettingsRoute.Page(SettingsPage.Workout),
          onRouteChanged = {},
          settings = RelvenSettingsSnapshot(false, true, false, false),
          onKeepScreenOnChanged = {},
          onAutoStartGpsChanged = {},
          onAutoPauseChanged = { requestedAutoPause = it },
          onAutoLapChanged = {})
    }

    composeRule.onNodeWithText("Automatisk paus").performClick()

    composeRule.runOnIdle { assertTrue(requestedAutoPause == true) }
  }
}
