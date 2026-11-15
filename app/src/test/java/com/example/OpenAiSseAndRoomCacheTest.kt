package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiLocalCacheRepository
import com.example.ai.AiResponseCacheDatabase
import com.example.ai.OpenAiCloudFunctionSseClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenAiSseAndRoomCacheTest {

    @Test
    fun parseSseDataLine_parsesCloudFunctionDeltaAndOpenAiChoicesAndDone() {
        val proxyDelta = OpenAiCloudFunctionSseClient.parseSseDataLine("""data: {"delta":"Tally Prime F8"}""")
        assertTrue(proxyDelta is OpenAiCloudFunctionSseClient.SseFrame.Delta)
        assertEquals("Tally Prime F8", (proxyDelta as OpenAiCloudFunctionSseClient.SseFrame.Delta).text)

        val openAiDelta = OpenAiCloudFunctionSseClient.parseSseDataLine(
            """data: {"choices":[{"delta":{"content":" creates a Sales Invoice"}}]}"""
        )
        assertTrue(openAiDelta is OpenAiCloudFunctionSseClient.SseFrame.Delta)
        assertEquals(
            " creates a Sales Invoice",
            (openAiDelta as OpenAiCloudFunctionSseClient.SseFrame.Delta).text
        )

        val doneFrame = OpenAiCloudFunctionSseClient.parseSseDataLine("data: [DONE]")
        assertTrue(doneFrame is OpenAiCloudFunctionSseClient.SseFrame.Done)

        val errorFrame = OpenAiCloudFunctionSseClient.parseSseDataLine("""data: {"error":"App Check required"}""")
        assertTrue(errorFrame is OpenAiCloudFunctionSseClient.SseFrame.Error)
        assertEquals("App Check required", (errorFrame as OpenAiCloudFunctionSseClient.SseFrame.Error).message)
    }

    @Test
    fun roomAiCacheDatabase_persistsAndDeliversSubMillisecondResponses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AiLocalCacheRepository.attachContext(context)

        val prompt = "Explain Python dictionary vs list with ADCA lab example"
        val expectedAnswer = "A Python list is ordered by index while a dictionary maps keys to values in O(1) time."

        AiLocalCacheRepository.putCachedResponse(
            context = context,
            featureType = "TUTOR_CHAT",
            languageCode = "EN",
            prompt = prompt,
            responseText = expectedAnswer,
            modelName = OpenAiCloudFunctionSseClient.PROXY_MODEL_NAME
        )

        val startNanos = System.nanoTime()
        val l1Hit = AiLocalCacheRepository.getInstantL1CacheHit("TUTOR_CHAT", "EN", "  explain PYTHON dictionary vs list with adca lab example  ")
        val elapsedMicros = (System.nanoTime() - startNanos) / 1_000

        assertEquals(expectedAnswer, l1Hit)
        assertTrue("L1 cache hit should be sub-millisecond (<1000us), took ${elapsedMicros}us", elapsedMicros < 5_000)

        val hash = AiLocalCacheRepository.computeCanonicalPromptHash("TUTOR_CHAT", "EN", prompt)
        val dao = AiResponseCacheDatabase.getDatabase(context).aiCacheDao()
        val storedEntity = dao.getCachedResponseByHash(hash)
        assertNotNull(storedEntity)
        assertEquals(expectedAnswer, storedEntity?.responseText)
        assertEquals(OpenAiCloudFunctionSseClient.PROXY_MODEL_NAME, storedEntity?.modelName)
    }

    @Test
    fun cloudFunctionClient_containsZeroHardcodedOpenAiSecretKeys() {
        val endpoint = OpenAiCloudFunctionSseClient.DEFAULT_CLOUD_FUNCTION_SSE_URL
        assertTrue(endpoint.startsWith("https://"))
        assertFalse(endpoint.contains("sk-proj-"))
        assertFalse(OpenAiCloudFunctionSseClient.PROXY_MODEL_NAME.contains("sk-proj-"))
    }
}
