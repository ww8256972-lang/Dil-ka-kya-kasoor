package com.example.security

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import org.lsposed.lsparanoid.Obfuscate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Google Tink 256-Bit Cryptographic Study Material Vault (`TinkMaterialVault`).
 *
 * Uses `KeyTemplates.get("AES256_GCM_HKDF_4KB")` backed by the Android Keystore
 * (`android-keystore://wci_tink_master_key`) to encrypt and decrypt downloaded Study Material
 * PDFs and lab attachments in 4KB authenticated chunks, preventing piracy via USB/MT Manager.
 */
@Obfuscate
object TinkMaterialVault {
    private const val KEYSET_NAME = "wci_tink_streaming_keyset"
    private const val PREF_FILE = "wci_tink_vault_prefs"
    private const val MASTER_KEY_URI = "android-keystore://wci_tink_master_key"

    @Volatile
    private var streamingAead: StreamingAead? = null

    @Volatile
    var isHardwareMasterKeyBound: Boolean = false
        private set

    private fun getEngine(context: Context): StreamingAead {
        val existing = streamingAead
        if (existing != null) return existing
        return synchronized(this) {
            val secondCheck = streamingAead
            if (secondCheck != null) {
                secondCheck
            } else {
                val appCtx = context.applicationContext ?: context
                StreamingAeadConfig.register()
                val keyTemplate = KeyTemplates.get("AES256_GCM_HKDF_4KB")
                val handle = runCatching {
                    AndroidKeysetManager.Builder()
                        .withSharedPref(appCtx, KEYSET_NAME, PREF_FILE)
                        .withKeyTemplate(keyTemplate)
                        .withMasterKeyUri(MASTER_KEY_URI)
                        .build()
                        .keysetHandle
                }.getOrElse {
                    // Self-heal corrupted OEM keystore entry cleanly while preserving AndroidKeyStore binding
                    appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().clear().commit()
                    AndroidKeysetManager.Builder()
                        .withSharedPref(appCtx, KEYSET_NAME, PREF_FILE)
                        .withKeyTemplate(keyTemplate)
                        .withMasterKeyUri(MASTER_KEY_URI)
                        .build()
                        .keysetHandle
                }
                isHardwareMasterKeyBound = true
                @Suppress("DEPRECATION")
                val created = handle.getPrimitive(StreamingAead::class.java)
                streamingAead = created
                created
            }
        }
    }

    fun encryptMaterialToDisk(
        context: Context,
        plainInput: InputStream,
        targetFile: File,
        materialId: String
    ) {
        val aad = "wci_material:$materialId".toByteArray(Charsets.UTF_8)
        getEngine(context).newEncryptingStream(targetFile.outputStream(), aad).use { encOut ->
            plainInput.copyTo(encOut)
        }
    }

    fun openDecryptedMaterialStream(
        context: Context,
        encryptedFile: File,
        materialId: String
    ): InputStream {
        val aad = "wci_material:$materialId".toByteArray(Charsets.UTF_8)
        return getEngine(context).newDecryptingStream(encryptedFile.inputStream(), aad)
    }

    fun encryptBytesToVaultFile(
        context: Context,
        plainBytes: ByteArray,
        targetFile: File,
        materialId: String
    ) {
        ByteArrayInputStream(plainBytes).use { input ->
            encryptMaterialToDisk(context, input, targetFile, materialId)
        }
    }

    fun decryptVaultFileToBytes(
        context: Context,
        encryptedFile: File,
        materialId: String
    ): ByteArray {
        openDecryptedMaterialStream(context, encryptedFile, materialId).use { decIn ->
            val out = ByteArrayOutputStream()
            decIn.copyTo(out)
            return out.toByteArray()
        }
    }
}
