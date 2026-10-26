package org.runnerup.relven.ui

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import org.runnerup.workout.Workout

/** Adds the start fragment only after AndroidView's container joins the activity hierarchy. */
internal class StartFragmentContainerController(
    private val container: FragmentContainerView,
    private val fragmentManager: FragmentManager,
    private val fragmentFactory: () -> Fragment,
    private val onFragmentReady: (Fragment, Workout?) -> Unit
) : View.OnAttachStateChangeListener {
  private var plannedWorkout: Workout? = null

  init {
    container.addOnAttachStateChangeListener(this)
  }

  fun updatePlannedWorkout(workout: Workout?) {
    plannedWorkout = workout
    ensureFragmentWhenAttached()
  }

  override fun onViewAttachedToWindow(view: View) {
    ensureFragmentWhenAttached()
  }

  override fun onViewDetachedFromWindow(view: View) = Unit

  private fun ensureFragmentWhenAttached() {
    if (!container.isAttachedToWindow || fragmentManager.isStateSaved) return

    if (fragmentManager.findFragmentById(container.id) == null) {
      fragmentManager.commitNow {
        setReorderingAllowed(true)
        replace(container.id, fragmentFactory())
      }
    }
    fragmentManager.findFragmentById(container.id)?.let { fragment ->
      val fragmentView = fragment.view
      if (fragmentView != null && fragmentView.parent != null && !isDescendantOf(fragmentView, container)) {
        // Compose can dispose this AndroidView while the Fragment remains registered with the
        // activity. Recreate its view in the newly attached container, preserving the Fragment
        // instance and its saved view state.
        fragmentManager.commitNow {
          setReorderingAllowed(true)
          detach(fragment)
        }
        fragmentManager.commitNow {
          setReorderingAllowed(true)
          attach(fragment)
        }
      }
      onFragmentReady(fragment, plannedWorkout)
    }
  }

  private fun isDescendantOf(view: View, ancestor: View): Boolean {
    var current: View? = view
    while (current != null) {
      if (current === ancestor) return true
      current = current.parent as? View
    }
    return false
  }
}
