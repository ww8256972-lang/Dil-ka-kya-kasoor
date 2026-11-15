package com.example.ai

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.lsposed.lsparanoid.Obfuscate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Zero-Trust Firebase Cloud Functions Server-Sent Events (SSE) Streaming Client
 * (`OpenAiCloudFunctionSseClient.kt`).
 *
 * Security Guarantees:
 * - Zero OpenAI API keys (`sk-proj-...`) are stored in the APK, BuildConfig, or client memory.
 * - Attaches a live Firebase App Check attestation token (`X-Firebase-AppCheck`) to every request
 *   so the Cloud Function proxy (`streamOpenAiTutor`) verifies client authenticity before calling OpenAI.
 * - Streams real-time SSE chunks (`text/event-stream`) line-by-line for instant progressive rendering.
 */
@Obfuscate
object OpenAiCloudFunctionSseClient {

    const val DEFAULT_CLOUD_FUNCTION_SSE_URL =
        "https://asia-east1-gen-lang-client-0309263247.cloudfunctions.net/streamOpenAiTutor"
    const val PROXY_MODEL_NAME = "gpt-4o-mini"

    private val sseHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    sealed class SseFrame {
        data class Delta(val text: String) : SseFrame()
        object Done : SseFrame()
        data class Error(val message: String) : SseFrame()
        object Ignored : SseFrame()
    }

    /**
     * Parses a single Server-Sent Events (`data: ...`) line from either our Firebase Cloud Function
     * SSE proxy (`{"delta":"..."}`) or standard OpenAI SSE format (`{"choices":[{"delta":{"content":"..."}}]}`).
     */
    fun parseSseDataLine(rawLine: String): SseFrame {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return SseFrame.Ignored
        if (!trimmed.startsWith("data:")) return SseFrame.Ignored

        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty()) return SseFrame.Ignored
        if (payload == "[DONE]") return SseFrame.Done

        return try {
            val json = JSONObject(payload)
            if (json.has("error")) {
                val errVal = json.optString("error").ifBlank { "Proxy SSE error" }
                return SseFrame.Error(errVal)
            }
            if (json.has("delta")) {
                val deltaText = json.optString("delta", "")
                return if (deltaText.isNotEmpty()) SseFrame.Delta(deltaText) else SseFrame.Ignored
            }
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val first = choices.optJSONObject(0)
                val deltaObj = first?.optJSONObject("delta") ?: first?.optJSONObject("message")
                val content = deltaObj?.optString("content", "").orEmpty()
                if (content.isNotEmpty()) {
                    return SseFrame.Delta(content)
                }
            }
            SseFrame.Ignored
        } catch (_: Exception) {
            SseFrame.Ignored
        }
    }

    private suspend fun fetchAppCheckTokenSafe(): String? {
        return withTimeoutOrNull(4_000L) {
            runCatching {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun fetchIdTokenSafe(): String? {
        return withTimeoutOrNull(3_000L) {
            runCatching {
                FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Streams progressive AI completion text from the Firebase Cloud Functions SSE proxy
     * protected by Firebase App Check.
     */
    fun streamFromCloudFunctionProxy(
        systemPrompt: String,
        userPrompt: String,
        role: String,
        endpointUrl: String = DEFAULT_CLOUD_FUNCTION_SSE_URL
    ): Flow<String> = flow {
        val appCheckToken = fetchAppCheckTokenSafe()
            ?: throw IllegalStateException("App Check token unavailable for Cloud Function SSE proxy.")
        val idToken = fetchIdTokenSafe()

        val requestJson = JSONObject().apply {
            put("model", PROXY_MODEL_NAME)
            put("systemPrompt", systemPrompt)
            put("userPrompt", userPrompt)
            put("role", role)
        }

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("X-Firebase-AppCheck", appCheckToken)
            .header("X-WCI-Portal-Role", role)
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!idToken.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $idToken")
        }

        val accumulated = StringBuilder()
        sseHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("SSE proxy returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty SSE proxy stream")
            BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    when (val frame = parseSseDataLine(line!!)) {
                        is SseFrame.Delta -> {
                            accumulated.append(frame.text)
                            emit(accumulated.toString())
                        }
                        is SseFrame.Done -> break
                        is SseFrame.Error -> throw IllegalStateException(frame.message)
                        is SseFrame.Ignored -> Unit
                    }
                }
            }
        }

        if (accumulated.isEmpty()) {
            throw IllegalStateException("No SSE tokens received from Cloud Function proxy.")
        }
    }.flowOn(Dispatchers.IO)
}
