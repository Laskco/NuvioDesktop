package com.nuvio.app.features.player.skip

internal fun SkipInterval.isManuallySkippable(): Boolean = type.trim().lowercase() != "post-credits"

internal fun List<SkipInterval>.activeManualSkipInterval(positionMs: Long): SkipInterval? {
    val positionSec = positionMs / 1000.0
    return firstOrNull { interval ->
        interval.isManuallySkippable() && positionSec >= interval.startTime && positionSec < interval.endTime
    }
}

internal fun SkipInterval.followingPostCreditsScene(
    intervals: List<SkipInterval>,
    durationMs: Long,
): SkipInterval? {
    if (type != "movie-credits" || !hasValidMovieTimes()) return null
    return intervals.asSequence().filter { scene ->
        scene.type == "post-credits" && scene.hasValidMovieTimes() &&
            scene.startTime >= endTime &&
            (durationMs <= 0L || scene.startTime * 1000.0 < durationMs.toDouble())
    }.minByOrNull { it.startTime }
}

private fun SkipInterval.hasValidMovieTimes(): Boolean =
    startTime.isFinite() && endTime.isFinite() && startTime >= 0.0 && endTime > startTime &&
        endTime * 1000.0 < Long.MAX_VALUE.toDouble()

internal fun SkipInterval.skipTargetPositionMs(
    durationMs: Long,
    intervals: List<SkipInterval> = emptyList(),
): Long {
    val targetTime = followingPostCreditsScene(intervals, durationMs)?.startTime ?: endTime
    val endMs = (targetTime * 1000.0).toLong()
    return if (type == "movie-credits" && durationMs > 0L) {
        endMs.coerceIn(0L, durationMs - 1L)
    } else endMs
}

internal fun IntroDbSegmentsResponse.toMovieSkipIntervals(): List<SkipInterval> {
    val credits = outro.toMovieIntervalOrNull("movie-credits")
    val scene = postCredits.toMovieIntervalOrNull("post-credits")
    // End-credit skipping must stop before the post-credits scene.
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
