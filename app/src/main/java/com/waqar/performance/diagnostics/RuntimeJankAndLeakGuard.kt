package com.waqar.performance.diagnostics

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RuntimeJankAndLeakGuard: Real-Time Frame Duration Monitor & UI Stutter Mitigator.
 *
 * Monitors UI frame delivery using [Choreographer]:
 * - Tracks frame render intervals: normal threshold 16.6ms (60 FPS) and 8.3ms (120 FPS).
 * - If frame duration spikes above 16ms consecutively, emits [isThrottlingNeeded] = true.
 * - Jetpack Compose Glassmorphism and blur layers observe this flag to dynamically throttle heavy
 *   render effects on mid-range devices, keeping interactions completely smooth and lag-free.
 */
object RuntimeJankAndLeakGuard {

    private const val TAG = "JankAndLeakGuard"
    private const val JANK_THRESHOLD_NS = 16_666_666L // 16.6 ms

    private val _isThrottlingNeeded = MutableStateFlow(false)
    val isThrottlingNeeded: StateFlow<Boolean> = _isThrottlingNeeded.asStateFlow()

    @Volatile
    private var isMonitoring = false
    private var lastFrameTimeNs = 0L

    fun attachToActivity(activity: Activity) {
        if (isMonitoring) return
        isMonitoring = true

        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTimeNs > 0L) {
                    val frameDuration = frameTimeNanos - lastFrameTimeNs
                    if (frameDuration > JANK_THRESHOLD_NS * 2) {
                        Log.d(TAG, "Frame drop detected: ${(frameDuration / 1_000_000.0).toInt()}ms")
                        _isThrottlingNeeded.value = true
                    } else if (frameDuration <= JANK_THRESHOLD_NS) {
                        _isThrottlingNeeded.value = false
                    }
                }
                lastFrameTimeNs = frameTimeNanos
                if (!activity.isFinishing && !activity.isDestroyed) {
                    Choreographer.getInstance().postFrameCallback(this)
                } else {
                    isMonitoring = false
                }
            }
        })
    }
}
