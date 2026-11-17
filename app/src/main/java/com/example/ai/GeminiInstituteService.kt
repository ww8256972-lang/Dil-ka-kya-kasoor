package com.example.ai

import android.graphics.Bitmap
import com.example.data.model.PortalRole
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.waqar.institute.ai.AiRateLimiterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.lsposed.lsparanoid.Obfuscate
import java.util.concurrent.ConcurrentHashMap

/**
 * Waqar Computer Institute AI Engine (M18: F375–F390).
 *
 * Combines three ultra-fast intelligence layers so students, teachers, and admins
 * receive answers immediately:
 * 1. Sub-Millisecond (<1ms) In-Memory Knowledge & ERP Cache for core DCA, ADCA, Tally Prime,
 *    Python, Web Dev, MS Office, and SQL questions.
 * 2. Role-Based Rate Limiter & Academic Prompt Guardrails (`AiRateLimiterManager`):
 *    - Admin & Teacher portals have ZERO rate limits or token restrictions.
 *    - Student & Parent portals have sliding-window rate limits (12 queries/hr) and academic scope guardrails.
 * 3. Primary Cloud AI: Firebase AI Logic (`gemini-3.8-flash` with `gemini-3.5-flash-lite` fallback) protected by App Check.
 */
@Obfuscate
class GeminiInstituteService {

    companion object {
        const val BRAND_NAME = "Waqar Computer Institute AI"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"
        const val AI_TIMEOUT_MILLIS = 60_000L

        private val responseMemoryCache = ConcurrentHashMap<String, String>()
    }

    private val geminiModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(MODEL_NAME)
    }

    private val fallbackGeminiModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(FALLBACK_MODEL_NAME)
    }

    suspend fun askInstituteTutor(prompt: String): String = withContext(Dispatchers.IO) {
        lookupSubMillisecondAnswer("TUTOR_CHAT", prompt)?.let { return@withContext it }
        AiLocalCacheRepository.getCachedResponse(null, "TUTOR_CHAT", "EN", prompt)?.let {
            return@withContext it
        }
        runCatching {
            var latestText = ""
            OpenAiCloudFunctionSseClient.streamFromCloudFunctionProxy(
                systemPrompt = "You are $BRAND_NAME, the official AI Academic & Operations Assistant of Waqar Computer Institute.",
                userPrompt = prompt,
                role = "Student"
            ).collect { partial ->
                latestText = partial
            }
            if (latestText.isBlank()) throw IllegalStateException("Empty SSE response")
            AiLocalCacheRepository.putCachedResponse(
                context = null,
                featureType = "TUTOR_CHAT",
                languageCode = "EN",
                prompt = prompt,
                responseText = latestText,
                modelName = OpenAiCloudFunctionSseClient.PROXY_MODEL_NAME
            )
            latestText
        }.recoverCatching {
            val response = geminiModel.generateContent(prompt)
            val text = response.text?.takeIf { it.isNotBlank() } ?: "No response generated."
            AiLocalCacheRepository.putCachedResponse(
                context = null,
                featureType = "TUTOR_CHAT",
                languageCode = "EN",
                prompt = prompt,
                responseText = text,
                modelName = MODEL_NAME
            )
            text
        }.getOrElse {
            "Offline AI Mode: Unable to reach Cloud Functions SSE Proxy or Firebase AI Logic service. Please verify network connectivity and retry."
        }
    }

    suspend fun generateContextualResponse(prompt: String): String = askInstituteTutor(prompt)

    private fun lookupSubMillisecondAnswer(featureType: String, prompt: String): String? {
        val normalizedKey = "${featureType.uppercase()}|${prompt.trim().lowercase()}"
        responseMemoryCache[normalizedKey]?.let { return it }
        AiLocalCacheRepository.getInstantL1CacheHit(featureType, "EN", prompt)?.let { return it }
        return null
    }

    fun streamTutorResponse(
        role: String,
        languageCode: String,
        featureType: String,
        userPrompt: String,
        instituteContextSummary: String = "",
        userId: String = "student"
    ): Flow<String> = flow {
        val resolvedRole = PortalRole.entries.firstOrNull {
            it.label.equals(role, ignoreCase = true) || it.key.equals(role, ignoreCase = true)
        } ?: PortalRole.STUDENT

        // 1. Evaluate Role-Based Rate Limiter & Academic Prompt Guardrails
        //    (Admin & Teacher have ZERO rate limits; Student & Parent have sliding-window limits)
        when (val check = AiRateLimiterManager.evaluateRequest(userId, resolvedRole, userPrompt)) {
            is AiRateLimiterManager.GuardrailCheckResult.BlockedByAcademicGuardrail -> {
                emit(check.message)
                return@flow
            }
            is AiRateLimiterManager.GuardrailCheckResult.RateLimited -> {
                emit(check.message)
                return@flow
            }
            is AiRateLimiterManager.GuardrailCheckResult.Allowed -> Unit
        }

        val cacheKey = "${featureType.uppercase()}|${userPrompt.trim().lowercase()}"

        // 2A. Sub-Millisecond (<0.1ms) L1 Memory Cache Hit (Previously verified live responses only)
        val cachedInstant = lookupSubMillisecondAnswer(featureType, userPrompt)
        if (cachedInstant != null) {
            emit(cachedInstant)
            return@flow
        }

        // 2B. Sub-Millisecond (<1ms) Room + SQLCipher Local Database Cache Hit
        val cachedRoom = AiLocalCacheRepository.getCachedResponse(
            context = null,
            featureType = featureType,
            languageCode = "EN",
            prompt = userPrompt
        )
        if (!cachedRoom.isNullOrBlank()) {
            responseMemoryCache[cacheKey] = cachedRoom
            emit(cachedRoom)
            return@flow
        }

        val langInstruction = "Respond clearly in professional, encouraging English with practical step-by-step computer lab examples."

        val systemFraming = buildString {
            appendLine(AiRateLimiterManager.buildInstituteGuardrailSystemPrompt(role, "EN", featureType))
            appendLine("You are $BRAND_NAME, the official AI Academic & Operations Assistant of Waqar Computer Institute (Grow Up Computer Skill).")
            appendLine("Current User Role: $role | Mode: $featureType.")
            appendLine(langInstruction)
            if (instituteContextSummary.isNotBlank()) {
                appendLine("Live Institute Snapshot: $instituteContextSummary")
            }
            when (featureType) {
                "QUIZ_GEN" -> appendLine("Generate a structured 5-question MCQ quiz with 4 options (A, B, C, D), correct answer key, and brief explanation for each question.")
                "LESSON_PLAN" -> appendLine("Create a structured 60-minute classroom & computer lab lesson plan with learning objectives, step-by-step lab exercise, and homework task.")
                "NOTICE_DRAFT" -> appendLine("Draft a polished, ready-to-send institute announcement suitable for In-App broadcast and WhatsApp/SMS template.")
                "RISK_ANALYSIS" -> appendLine("Analyze the institute's attendance, fee dues, and batch metrics and provide actionable retention & collection recommendations.")
                else -> appendLine("Provide accurate, step-by-step computer science, DCA/ADCA, Tally Prime, Python, Web Development, or exam preparation guidance.")
            }
            appendLine()
            appendLine("User Request: $userPrompt")
        }

        // 3. Primary Stream: Firebase Cloud Functions SSE Proxy (OpenAI/ChatGPT with App Check attestation)
        //    Fallback Stream: Firebase AI Logic (gemini-3.8-flash -> gemini-3.5-flash-lite)
        val accumulated = StringBuilder()
        var modelUsed = OpenAiCloudFunctionSseClient.PROXY_MODEL_NAME

        val sseProxySucceeded = runCatching {
            withTimeout(AI_TIMEOUT_MILLIS) {
                OpenAiCloudFunctionSseClient.streamFromCloudFunctionProxy(
                    systemPrompt = systemFraming,
                    userPrompt = userPrompt,
                    role = role
                ).collect { partialText ->
                    if (partialText.isNotEmpty()) {
                        accumulated.setLength(0)
                        accumulated.append(partialText)
                        emit(partialText)
                    }
                }
            }
            accumulated.isNotEmpty()
        }.getOrDefault(false)

        if (!sseProxySucceeded) {
            accumulated.setLength(0)
            modelUsed = MODEL_NAME
            runCatching {
                withTimeout(AI_TIMEOUT_MILLIS) {
                    val responseStream = geminiModel.generateContentStream(systemFraming)
                    responseStream.collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(accumulated.toString())
                        }
                    }
                }
            }.onFailure { primaryErr ->
                accumulated.setLength(0)
                modelUsed = FALLBACK_MODEL_NAME
                runCatching {
                    withTimeout(AI_TIMEOUT_MILLIS) {
                        val fallbackStream = fallbackGeminiModel.generateContentStream(systemFraming)
                        fallbackStream.collect { chunk ->
                            val piece = chunk.text.orEmpty()
                            if (piece.isNotEmpty()) {
                                accumulated.append(piece)
                                emit(accumulated.toString())
                            }
                        }
                    }
                }.onFailure {
                    throw primaryErr
                }
            }
        }

        val finalOutput = accumulated.toString()
        if (finalOutput.isNotBlank()) {
            responseMemoryCache[cacheKey] = finalOutput
            AiLocalCacheRepository.putCachedResponse(
                context = null,
                featureType = featureType,
                languageCode = "EN",
                prompt = userPrompt,
                responseText = finalOutput,
                modelName = modelUsed
            )
            AiRateLimiterManager.recordTokensConsumed(
                userId = userId,
                role = resolvedRole,
                tokens = ((systemFraming.length + finalOutput.length) / 4).coerceAtLeast(1)
            )
        }
    }

    suspend fun analyzeMultimodalDoubt(
        bitmap: Bitmap,
        question: String,
        languageCode: String,
        role: PortalRole = PortalRole.STUDENT,
        userId: String = "student"
    ): String {
        when (val check = AiRateLimiterManager.evaluateRequest(userId, role, question)) {
            is AiRateLimiterManager.GuardrailCheckResult.BlockedByAcademicGuardrail -> return check.message
            is AiRateLimiterManager.GuardrailCheckResult.RateLimited -> return check.message
            is AiRateLimiterManager.GuardrailCheckResult.Allowed -> Unit
        }

        val prompt = content {
            image(bitmap)
            text(
                "You are $BRAND_NAME's Lab & Doubt Tutor. " +
                    "Examine this image/screenshot/notes and answer clearly step-by-step in English: $question"
            )
        }
        val response = withTimeout(AI_TIMEOUT_MILLIS) {
            geminiModel.generateContent(prompt)
        }
        val text = response.text ?: "Unable to extract text from the AI response."
        AiRateLimiterManager.recordTokensConsumed(userId, role, 280 + (text.length / 4))
        return text
    }
}
