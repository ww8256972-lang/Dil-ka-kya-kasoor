package com.waqar.institute.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiInstituteService
import com.example.ai.OpenAiCloudFunctionSseClient
import com.example.data.model.PortalRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waqar.institute.ui.student.TutorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.lsposed.lsparanoid.Obfuscate

/**
 * Production-Ready AI Tutor ViewModel & Zero-Billing Client-Side Guardian.
 *
 * Implements:
 * 1. Client-Side Anti-Spam Debounce & Local Cooldown Pacer (5-second throttle between queries).
 * 2. Instant Local Pre-Filtering against Prompt Injections and Security Attack Heuristics (0 Network Cost).
 * 3. Reactive SSE Streaming into Jetpack Compose UI state.
 * 4. Hardware/Server-Mirrored 24-Hour Security Lockout with Live 1-Second Countdown Ticker.
 */
@Obfuscate
class AiTutorViewModel(
    private val context: Context? = null
) : ViewModel() {

    data class AiTutorUiState(
        val messages: List<TutorMessage> = listOf(
            TutorMessage(
                sender = "AI Tutor",
                text = "Hello! I am your Waqar Institute 24/7 Study Tutor. Ask me any conceptual question about ADCA, DCA, CCC, Tally Prime, Python, C++, Java, or Web Development.",
                tierSource = "L1 System"
            )
        ),
        val isStreaming: Boolean = false,
        val isLocked: Boolean = false,
        val lockedUntilEpochMs: Long = 0L,
        val lockoutCountdownFormatted: String = "",
        val localCooldownSeconds: Int = 0,
        val canSend: Boolean = true,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(AiTutorUiState())
    val uiState: StateFlow<AiTutorUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var cooldownJob: Job? = null
    private var streamingJob: Job? = null
    private var lastSendTimestamp = 0L

    init {
        checkAndSyncLockoutStatus()
    }

    /**
     * Checks if the student is currently under a 24-hour security lockout.
     */
    fun checkAndSyncLockoutStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val db = com.example.data.firestore.InstituteRepository.getConfiguredFirestore(context)
                val snap = db.collection("ai_tutor_locks").document(uid).get().await()
                if (snap.exists()) {
                    val locked = snap.getBoolean("locked") ?: false
                    val lockedUntil = snap.getLong("lockedUntilMs") ?: 0L
                    val now = System.currentTimeMillis()
                    if (locked && lockedUntil > now) {
                        applyLockout(lockedUntil)
                    } else if (lockedUntil <= now && _uiState.value.isLocked) {
                        clearLockout()
                    }
                }
            }.onFailure {
                // If offline, check local encrypted preferences via AiRateLimiterManager
                val guard = AiRateLimiterManager.evaluatePreCacheGuardrails(
                    userId = uid,
                    role = PortalRole.STUDENT,
                    promptText = "test",
                    context = context
                )
                if (guard is AiRateLimiterManager.GuardrailCheckResult.LockedOut24Hours) {
                    val now = System.currentTimeMillis()
                    val remainingMs = guard.remainingMinutes * 60 * 1000L
                    applyLockout(now + remainingMs)
                }
            }
        }
    }

    private fun applyLockout(lockedUntilEpochMs: Long) {
        _uiState.update {
            it.copy(
                isLocked = true,
                lockedUntilEpochMs = lockedUntilEpochMs,
                canSend = false,
                errorMessage = "AI Study Tutor is locked due to security policy violation."
            )
        }
        startCountdownTicker(lockedUntilEpochMs)
    }

    private fun clearLockout() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                isLocked = false,
                lockedUntilEpochMs = 0L,
                lockoutCountdownFormatted = "",
                canSend = it.localCooldownSeconds <= 0,
                errorMessage = null
            )
        }
    }

    private fun startCountdownTicker(lockedUntilEpochMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remainingMs = (lockedUntilEpochMs - now).coerceAtLeast(0L)
                if (remainingMs <= 0L) {
                    clearLockout()
                    break
                }
                val totalSeconds = remainingMs / 1000
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                val formatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                _uiState.update { it.copy(lockoutCountdownFormatted = formatted) }
                delay(1000L)
            }
        }
    }

    private fun startLocalCooldownTimer(durationSeconds: Int = 5) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (sec in durationSeconds downTo 1) {
                _uiState.update { it.copy(localCooldownSeconds = sec, canSend = false) }
                delay(1000L)
            }
            _uiState.update {
                it.copy(
                    localCooldownSeconds = 0,
                    canSend = !it.isLocked && !it.isStreaming
                )
            }
        }
    }

    /**
     * Sends student query through the 6-stage Zero-Bill AI pipeline.
     */
    fun sendQuery(rawPrompt: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isBlank()) return

        val state = _uiState.value
        if (state.isLocked || state.isStreaming || state.localCooldownSeconds > 0) {
            return
        }

        // 1. Client-Side Debounce Guard (<500ms rapid tap protection)
        val now = System.currentTimeMillis()
        if (now - lastSendTimestamp < 500L) return
        lastSendTimestamp = now

        // 2. Client-Side Zero-Cost Security Heuristic Pre-Filter
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous_student"
        if (AiRateLimiterManager.isSecurityAttackPrompt(trimmed)) {
            val lockedUntil = now + 24 * 60 * 60 * 1000L
            AiRateLimiterManager.mirrorServerSecurityLock(
                userId = uid,
                role = PortalRole.STUDENT,
                strikeCount = 2,
                locked = true,
                lockedUntilEpochMs = lockedUntil,
                context = context
            )
            val updatedMessages = state.messages +
                TutorMessage("Student", trimmed, "Local Input") +
                TutorMessage("Security Firewall", AiRateLimiterManager.SECURITY_STRIKE_2_LOCKOUT_MESSAGE, "L0 Firewall")
            _uiState.update {
                it.copy(
                    messages = updatedMessages,
                    isLocked = true,
                    lockedUntilEpochMs = lockedUntil,
                    canSend = false
                )
            }
            startCountdownTicker(lockedUntil)
            return
        }

        // 3. Client-Side Anti-Chit-Chat / Academic Allowlist Pre-Filter (0 API Cost)
        if (AiRateLimiterManager.isCasualChitChatOrOffTopic(trimmed)) {
            val updatedMessages = state.messages +
                TutorMessage("Student", trimmed, "Local Input") +
                TutorMessage("AI Tutor", AiRateLimiterManager.POLITE_STUDY_REDIRECT_MESSAGE, "L0 Allowlist")
            _uiState.update { it.copy(messages = updatedMessages) }
            startLocalCooldownTimer(3)
            return
        }

        // 4. Append user query to UI message feed
        val updatedMessages = state.messages + TutorMessage("Student", trimmed, "Local Input")
        val placeholderIndex = updatedMessages.size
        val messagesWithPlaceholder = updatedMessages + TutorMessage("AI Tutor", "Thinking...", "Connecting...")

        _uiState.update {
            it.copy(
                messages = messagesWithPlaceholder,
                isStreaming = true,
                canSend = false,
                errorMessage = null
            )
        }

        // 5. Stream from Backend Cloud Function Proxy with Grok -> Gemini Failover
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            startLocalCooldownTimer(5)
            val geminiService = GeminiInstituteService(context = context)
            val accumulated = StringBuilder()

            try {
                geminiService.streamTutorResponse(
                    role = PortalRole.STUDENT.label,
                    languageCode = "EN",
                    featureType = "TUTOR_CHAT",
                    userPrompt = trimmed,
                    userId = uid
                ).collect { chunk ->
                    accumulated.setLength(0)
                    accumulated.append(chunk)

                    _uiState.update { current ->
                        val currentList = current.messages.toMutableList()
                        if (placeholderIndex < currentList.size) {
                            currentList[placeholderIndex] = TutorMessage(
                                sender = "AI Tutor",
                                text = accumulated.toString(),
                                tierSource = "Zero-Bill AI Proxy"
                            )
                        }
                        current.copy(messages = currentList)
                    }
                }
            } catch (secEx: OpenAiCloudFunctionSseClient.ProxySecurityViolationException) {
                if (secEx.locked && secEx.lockedUntilMs > 0L) {
                    applyLockout(secEx.lockedUntilMs)
                }
                _uiState.update { current ->
                    val currentList = current.messages.toMutableList()
                    if (placeholderIndex < currentList.size) {
                        currentList[placeholderIndex] = TutorMessage(
                            sender = "Security Firewall",
                            text = secEx.message,
                            tierSource = "L0 Security"
                        )
                    }
                    current.copy(messages = currentList, isStreaming = false)
                }
                return@launch
            } catch (rateEx: OpenAiCloudFunctionSseClient.ProxyRateLimitException) {
                _uiState.update { current ->
                    val currentList = current.messages.toMutableList()
                    if (placeholderIndex < currentList.size) {
                        currentList[placeholderIndex] = TutorMessage(
                            sender = "Quota Guardian",
                            text = rateEx.message,
                            tierSource = "Rate Limiter"
                        )
                    }
                    current.copy(messages = currentList, isStreaming = false)
                }
                startLocalCooldownTimer(rateEx.retryAfterSeconds.toInt().coerceAtMost(60))
                return@launch
            } catch (err: Exception) {
                _uiState.update { current ->
                    val currentList = current.messages.toMutableList()
                    if (placeholderIndex < currentList.size) {
                        currentList[placeholderIndex] = TutorMessage(
                            sender = "AI Tutor",
                            text = "DCA / Tally Study Note: ${err.localizedMessage ?: "Unable to connect to study server. Please verify your connection."}",
                            tierSource = "L6 Offline Vault"
                        )
                    }
                    current.copy(messages = currentList, isStreaming = false)
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        canSend = !it.isLocked && it.localCooldownSeconds <= 0
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        cooldownJob?.cancel()
        streamingJob?.cancel()
    }
}
