package com.wci.core.crypto.biometric

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * BiometricEnclaveBridge (S-29, S-36, S-50)
 * Bridges hardware-backed Keystore keys to BiometricPrompt.CryptoObject requiring
 * hardware BIOMETRIC_STRONG authentication before private key operations can execute.
 */
object BiometricEnclaveBridge {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val BIOMETRIC_AUTH_ALIAS = "wci_biometric_hardware_enclave_key"

    /**
     * Checks if biometric hardware is present, enrolled, and capable of AUTH_BIOMETRIC_STRONG.
     */
    fun canAuthenticate(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Provisions a biometric-bound ECDSA-P256 key inside TEE/StrongBox.
     * Requires AUTH_BIOMETRIC_STRONG per invocation (timeout = 0 seconds).
     */
    fun initBiometricKey(alias: String = BIOMETRIC_AUTH_ALIAS): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }

            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }

            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates a BiometricPrompt.CryptoObject initialized for signing.
     */
    fun createSigningCryptoObject(alias: String = BIOMETRIC_AUTH_ALIAS): BiometricPrompt.CryptoObject? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(alias)) {
                initBiometricKey(alias)
            }
            val privateKey = keyStore.getKey(alias, null) as? PrivateKey ?: return null
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            BiometricPrompt.CryptoObject(signature)
        } catch (e: Exception) {
            null
        }
    }
}
