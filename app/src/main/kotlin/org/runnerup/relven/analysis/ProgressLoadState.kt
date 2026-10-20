package org.runnerup.relven.analysis

internal sealed interface ProgressLoadState {
  data object Loading : ProgressLoadState

  data class Loaded(val summary: ProgressSummary) : ProgressLoadState

  data object Unavailable : ProgressLoadState
}

internal fun progressLoadState(result: Result<ProgressSummary>?): ProgressLoadState =
    when {
      result == null -> ProgressLoadState.Loading
      result.isSuccess -> ProgressLoadState.Loaded(requireNotNull(result.getOrNull()))
      else -> ProgressLoadState.Unavailable
    }
