package com.wci.core.crypto.jwt

import java.util.concurrent.atomic.AtomicReference

/**
 * EphemeralTokenRotator (S-10, S-16)
 * Rotates ephemeral ECDSA-P256 DPoP keys on a 15-minute sliding window or upon request.
 */
class EphemeralTokenRotator(
    private val rotationIntervalMs: Long = 15L * 60L * 1000L // 15 minutes
) {
    private class ActiveKey(
        val binder: DpopTokenBinder,
        val createdAtMs: Long
    )

    private val currentKey = AtomicReference(ActiveKey(DpopTokenBinder(), System.currentTimeMillis()))

    /**
     * Obtains the current valid DpopTokenBinder, rotating if interval elapsed.
     */
    fun getActiveBinder(): DpopTokenBinder {
        val now = System.currentTimeMillis()
        val existing = currentKey.get()

        return if (now - existing.createdAtMs >= rotationIntervalMs) {
            val fresh = ActiveKey(DpopTokenBinder(), now)
            if (currentKey.compareAndSet(existing, fresh)) {
                fresh.binder
            } else {
                currentKey.get().binder
            }
        } else {
            existing.binder
        }
    }

    /**
     * Forces immediate rotation of the DPoP keypair.
     */
    fun forceRotate(): DpopTokenBinder {
        val fresh = ActiveKey(DpopTokenBinder(), System.currentTimeMillis())
        currentKey.set(fresh)
        return fresh.binder
    }

    /**
     * Generates a fresh proof using the active rotated key.
     */
    fun createProof(
        httpMethod: String,
        httpUri: String,
        accessToken: String? = null,
        nonce: String? = null
    ): String {
        return getActiveBinder().generateDpopProof(httpMethod, httpUri, accessToken, nonce)
    }
}
