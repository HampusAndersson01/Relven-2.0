package org.runnerup.relven.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.runnerup.workout.RelvenWorkoutImporter
import org.runnerup.workout.Workout

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StartFragmentContainerControllerTest {
  @Test
  fun createsStartFragmentOnlyAfterContainerIsAttached() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val containerId = View.generateViewId()
    val container = FragmentContainerView(activity).apply { id = containerId }
    var deliveredWorkout: Workout? = null
    val workout =
        RelvenWorkoutImporter.parse(
            """{"schemaVersion":3,"id":"planned-run","name":"Easy run","sport":"running","steps":[{"id":"run","kind":"segment","phase":"work","end":{"type":"time","seconds":600}}]}""")
    val controller =
        StartFragmentContainerController(
            container,
            activity.supportFragmentManager,
            fragmentFactory = { Fragment() },
            onFragmentReady = { _, selectedWorkout -> deliveredWorkout = selectedWorkout })

    controller.updatePlannedWorkout(workout)
    assertNull(activity.supportFragmentManager.findFragmentById(containerId))
    assertNull(deliveredWorkout)

    activity.setContentView(container)
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    assertNotNull(activity.supportFragmentManager.findFragmentById(containerId))
    assertSame(workout, deliveredWorkout)

    controller.updatePlannedWorkout(null)
    assertNull(deliveredWorkout)
  }

  @Test
  fun attachesExistingStartFragmentToReplacementContainerAfterDestinationIsRecreated() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val containerId = View.generateViewId()
    val firstContainer = FragmentContainerView(activity).apply { id = containerId }
    val firstController =
        StartFragmentContainerController(
            firstContainer,
            activity.supportFragmentManager,
            fragmentFactory = { ContainerContentFragment() },
            onFragmentReady = { _, _ -> })

    firstController.updatePlannedWorkout(null)
    activity.setContentView(firstContainer)
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    val startFragment = activity.supportFragmentManager.findFragmentById(containerId)
    assertNotNull(startFragment)
    activity.setContentView(FrameLayout(activity))
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    assertTrue(isDescendantOf(startFragment?.view, firstContainer))

    val replacementContainer = FragmentContainerView(activity).apply { id = containerId }
    val replacementController =
        StartFragmentContainerController(
            replacementContainer,
            activity.supportFragmentManager,
            fragmentFactory = { ContainerContentFragment() },
            onFragmentReady = { _, _ -> })
    replacementController.updatePlannedWorkout(null)
    activity.setContentView(replacementContainer)
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    assertSame(startFragment, activity.supportFragmentManager.findFragmentById(containerId))
    assertTrue(
        "Replacement container did not host the Fragment view; parent chain=${describeParents(startFragment?.view)}, childCount=${replacementContainer.childCount}",
        isDescendantOf(startFragment?.view, replacementContainer))
    assertEquals(1, replacementContainer.childCount)
  }

  private fun isDescendantOf(view: View?, ancestor: View): Boolean {
    var current = view
    while (current != null) {
      if (current === ancestor) return true
      current = current.parent as? View
    }
    return false
  }

  private fun describeParents(view: View?): String {
    val parents = mutableListOf<String>()
    var current = view
    while (current != null) {
      parents += "${current.javaClass.simpleName}#${current.id}"
      current = current.parent as? View
    }
    return parents.joinToString(" -> ")
  }

  class ContainerContentFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View = View(requireContext())
  }
}
