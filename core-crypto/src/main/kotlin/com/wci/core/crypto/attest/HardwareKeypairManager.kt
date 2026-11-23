package com.wci.core.crypto.attest

import java.security.KeyStore

/**
 * HardwareKeypairManager (S-26, S-47)
 * High-level manager for provisioning, inspecting, and rotating zero-trust hardware keys.
 */
object HardwareKeypairManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun isKeyProvisioned(alias: String): Boolean {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.containsAlias(alias)
        } catch (e: Exception) {
            false
        }
    }

    fun deleteKey(alias: String): Boolean {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun purgeAllInstituteHardwareKeys() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith("wci_")) {
                    ks.deleteEntry(alias)
                }
            }
        } catch (_: Exception) {}
    }
}
