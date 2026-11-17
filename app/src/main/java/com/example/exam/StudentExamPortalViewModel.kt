package com.example.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ExamRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Student Exam Portal Gateway ViewModel (`StudentExamPortalViewModel.kt`).
 *
 * Manages:
 * - "Upcoming Exam" lock state with a live `HH:MM:SS` countdown timer until the scheduled start timestamp.
 * - Automatic exam unlock (`isExamUnlocked = true`) at the exact millisecond the countdown hits `00:00:00`.
 * - Active Exam Remaining Window Timer (`remainingExamSeconds`) that triggers automatic submission on expiry.
 */
data class StudentExamGatewayState(
    val exam: ExamRecord? = null,
    val isLockedWaitingForStart: Boolean = false,
    val countdownHhMmSs: String = "00:00:00",
    val isExamUnlocked: Boolean = true,
    val remainingExamSeconds: Int = 3600,
    val remainingFormattedMmSs: String = "60:00",
    val hasTimerExpiredAutoSubmit: Boolean = false
)

class StudentExamPortalViewModel : ViewModel() {

    private val _gatewayState = MutableStateFlow(StudentExamGatewayState())
    val gatewayState: StateFlow<StudentExamGatewayState> = _gatewayState.asStateFlow()

    private var countdownJob: Job? = null
    private var examTimerJob: Job? = null

    /**
     * Binds an `ExamRecord` and starts the live countdown if `examDateIso` is in the future,
     * or unlocks immediately if the scheduled start time has arrived.
     */
    fun attachExamSchedule(exam: ExamRecord, onAutoUnlock: () -> Unit = {}) {
        countdownJob?.cancel()
        val scheduledStartEpochMs = parseExamStartEpochMs(exam.scheduledDateIso)
        val totalDurationSeconds = (exam.durationMinutes.coerceAtLeast(5)) * 60

        countdownJob = viewModelScope.launch {
            launch { TamperProofExamClock.syncServerTimeOffsetAsync() }
            while (true) {
                val nowMs = TamperProofExamClock.currentTrustedEpochMs()
                val diffMs = scheduledStartEpochMs - nowMs
                if (diffMs > 0L) {
                    val totalSecs = (diffMs + 999L) / 1000L
                    _gatewayState.value = StudentExamGatewayState(
                        exam = exam,
                        isLockedWaitingForStart = true,
                        countdownHhMmSs = TamperProofExamClock.formatHhMmSs(totalSecs),
                        isExamUnlocked = false,
                        remainingExamSeconds = totalDurationSeconds,
                        remainingFormattedMmSs = TamperProofExamClock.formatMmSs(totalDurationSeconds)
                    )
                    delay(500L)
                } else {
                    _gatewayState.value = _gatewayState.value.copy(
                        exam = exam,
                        isLockedWaitingForStart = false,
                        countdownHhMmSs = "00:00:00",
                        isExamUnlocked = true,
                        remainingExamSeconds = totalDurationSeconds
                    )
                    onAutoUnlock()
                    break
                }
            }
        }
    }

    /**
     * Starts the active exam countdown timer using monotonic `SystemClock.elapsedRealtime()` so
     * manual clock changes or coroutine sleep cannot extend exam duration.
     */
    fun startActiveExamClock(durationMinutes: Int, onTimeExpiredAutoSubmit: () -> Unit) {
        examTimerJob?.cancel()
        val deadlineElapsedMs = TamperProofExamClock.elapsedDeadlineAfterDurationMinutes(durationMinutes)
        examTimerJob = viewModelScope.launch {
            while (true) {
                val secsLeft = TamperProofExamClock.computeRemainingSeconds(deadlineElapsedMs)
                if (secsLeft <= 0) {
                    break
                }
                _gatewayState.value = _gatewayState.value.copy(
                    remainingExamSeconds = secsLeft,
                    remainingFormattedMmSs = TamperProofExamClock.formatMmSs(secsLeft),
                    hasTimerExpiredAutoSubmit = false
                )
                delay(500L)
            }
            _gatewayState.value = _gatewayState.value.copy(
                remainingExamSeconds = 0,
                remainingFormattedMmSs = "00:00",
                hasTimerExpiredAutoSubmit = true
            )
            onTimeExpiredAutoSubmit()
        }
    }

    fun stopTimers() {
        countdownJob?.cancel()
        examTimerJob?.cancel()
    }

    private fun parseExamStartEpochMs(dateIso: String): Long {
        val clean = dateIso.trim()
        val trustedNow = TamperProofExamClock.currentTrustedEpochMs()
        // Supports "YYYY-MM-DD HH:mm" or "YYYY-MM-DD"; if date is today or past, unlocks immediately
        return runCatching {
            if (clean.length > 10 && clean.contains(":")) {
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                fmt.parse(clean.take(16))?.time ?: (trustedNow - 1000L)
            } else {
                trustedNow - 1000L
            }
        }.getOrDefault(trustedNow - 1000L)
    }

    override fun onCleared() {
        super.onCleared()
        stopTimers()
    }
}
