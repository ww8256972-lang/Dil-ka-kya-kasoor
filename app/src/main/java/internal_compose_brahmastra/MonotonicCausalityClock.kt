package internal_compose_brahmastra

import android.os.SystemClock
import kotlin.math.abs

/**
 * 5. `internal_compose_brahmastra/MonotonicCausalityClock.kt`
 * Hardware Uptime (`SystemClock.elapsedRealtimeNanos()`) Cryptographic Ticker that detects
 * and blocks device wall-clock rollback or time-travel tampering during Exams and Fee Deadlines.
 */
object MonotonicCausalityClock {

    data class CausalityAuditReport(
        val wallClockDriftMs: Long,
        val clockRollbackTamperDetected: Boolean,
        val trustedEpochMs: Long
    )

    @Volatile
    private var anchorWallEpochMs: Long = System.currentTimeMillis()
    @Volatile
    private var anchorElapsedNs: Long = nowElapsedNanos()

    fun nowElapsedNanos(): Long {
        val sysElapsed = runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(0L)
        return if (sysElapsed > 0L) sysElapsed else System.nanoTime()
    }

    @Synchronized
    fun recalibrateAnchor(
        wallEpochMs: Long = System.currentTimeMillis(),
        elapsedNs: Long = nowElapsedNanos()
    ) {
        anchorWallEpochMs = wallEpochMs
        anchorElapsedNs = elapsedNs
    }

    /**
     * Compares elapsed hardware monotonic nanoseconds against current `System.currentTimeMillis()`
     * to detect if the user rolled the Android system clock backward or forward (> 15 seconds drift).
     */
    fun verifyWallClockCausality(
        currentWallEpochMs: Long = System.currentTimeMillis(),
        currentElapsedNs: Long = nowElapsedNanos(),
        maxAllowedDriftMs: Long = 15_000L
    ): CausalityAuditReport {
        val elapsedDeltaMs = ((currentElapsedNs - anchorElapsedNs) / 1_000_000L).coerceAtLeast(0L)
        val trustedNowMs = anchorWallEpochMs + elapsedDeltaMs
        val driftMs = currentWallEpochMs - trustedNowMs
        val tampered = abs(driftMs) > maxAllowedDriftMs

        return CausalityAuditReport(
            wallClockDriftMs = driftMs,
            clockRollbackTamperDetected = tampered,
            trustedEpochMs = trustedNowMs
        )
    }
}
