package com.nuvio.app.features.player.skip

internal fun SkipInterval.skipTargetPositionMs(durationMs: Long): Long {
    val endMs = (endTime * 1000.0).toLong()
    return if ((type == "movie-credits" || type == "post-credits") && durationMs > 0L) {
        endMs.coerceIn(0L, durationMs - 1L)
    } else endMs
}

internal fun IntroDbSegmentsResponse.toMovieSkipIntervals(): List<SkipInterval> {
    val credits = outro.toMovieIntervalOrNull("movie-credits")
    val scene = postCredits.toMovieIntervalOrNull("post-credits")
    // End-credit skipping must preserve the separately controlled post-credits scene.
    val safeCredits = if (credits != null && scene != null &&
        scene.startTime < credits.endTime && scene.endTime > credits.startTime
    ) {
        credits.copy(endTime = scene.startTime).takeIf { it.endTime > it.startTime }
    } else credits
    return listOfNotNull(safeCredits, scene)
}

private fun IntroDbSegment?.toMovieIntervalOrNull(type: String): SkipInterval? {
    if (this == null) return null
    val start = startSec ?: startMs?.let { it / 1000.0 } ?: return null
    val end = endSec ?: endMs?.let { it / 1000.0 } ?: return null
    if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
    return SkipInterval(startTime = start, endTime = end, type = type, provider = "introdb")
}
