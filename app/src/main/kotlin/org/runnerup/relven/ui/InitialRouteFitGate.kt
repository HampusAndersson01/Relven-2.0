package org.runnerup.relven.ui

/** Waits for both the route style and a measured viewport before fitting a saved route once. */
internal class InitialRouteFitGate {
  private var styleReady = false
  private var viewportReady = false
  private var routeAvailable = false
  private var fitIssued = false

  fun onStyleLoaded(routePointCount: Int): Boolean {
    styleReady = true
    routeAvailable = routePointCount >= 2
    return takeFitIfReady()
  }

  fun onViewportChanged(width: Int, height: Int): Boolean {
    viewportReady = width > 0 && height > 0
    return takeFitIfReady()
  }

  private fun takeFitIfReady(): Boolean {
    if (fitIssued || !styleReady || !viewportReady || !routeAvailable) return false
    fitIssued = true
    return true
  }
}
