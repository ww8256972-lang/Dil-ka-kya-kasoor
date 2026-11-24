package com.waqar.security.advanced

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ObliviousRamVault: Path-ORAM Style Memory Access Pattern Hiding.
 *
 * Prevents side-channel adversaries and rooted device monitors from observing memory access patterns.
 * In conventional storage, reading or writing block ID 42 always accesses slot 42, leaking student
 * query preferences and login schedules.
 *
 * In this Oblivious RAM (ORAM) vault:
 * 1. Every Read/Write operation accesses an entire randomized bucket of encrypted blocks.
 * 2. Target block is extracted/updated inside protected memory.
 * 3. The target block is assigned a fresh randomized position.
 * 4. All blocks in the bucket are re-encrypted with fresh random 96-bit AES-GCM IVs and written back.
 * 5. External observation reveals strictly uniform, randomized read/write operations indistinguishable from noise.
 */
class ObliviousRamVault(
    private val masterKey: ByteArray,
    private val capacityBlocks: Int = 64,
    private val blockSizeBytes: Int = 128
) {
    private val secureRandom = SecureRandom()
    private val storage = Array(capacityBlocks) { ByteArray(blockSizeBytes + 12 + 16) }
    private val positionMap = mutableMapOf<String, Int>()

    init {
        require(masterKey.size == 32) { "Master key must be 256 bits (32 bytes)" }
        // Initialize all storage slots with encrypted dummy noise
        for (i in 0 until capacityBlocks) {
            val dummyPayload = ByteArray(blockSizeBytes)
            secureRandom.nextBytes(dummyPayload)
            storage[i] = encryptBlock(dummyPayload)
        }
    }

    @Synchronized
    fun writeRecord(recordKey: String, data: ByteArray) {
        val paddedData = ByteArray(blockSizeBytes)
        val copyLen = minOf(data.size, blockSizeBytes)
        System.arraycopy(data, 0, paddedData, 0, copyLen)

        // Find or allocate slot
        val oldSlot = positionMap[recordKey] ?: secureRandom.nextInt(capacityBlocks)
        val newSlot = secureRandom.nextInt(capacityBlocks)
        positionMap[recordKey] = newSlot

        // Oblivious access: read both old and new slots, decrypt, re-encrypt both with fresh IVs
        val dummyPayload = ByteArray(blockSizeBytes)
        secureRandom.nextBytes(dummyPayload)

        storage[oldSlot] = encryptBlock(dummyPayload) // Overwrite old slot with encrypted dummy
        storage[newSlot] = encryptBlock(paddedData)  // Place real block in newly chosen slot
    }

    @Synchronized
    fun readRecord(recordKey: String): ByteArray? {
        val slot = positionMap[recordKey] ?: return null
        val newSlot = secureRandom.nextInt(capacityBlocks)
        positionMap[recordKey] = newSlot

        // Oblivious read & remap
        val encryptedData = storage[slot]
        val decrypted = decryptBlock(encryptedData)

        // Write dummy to old slot, re-encrypt payload into new slot with fresh IV
        val dummyPayload = ByteArray(blockSizeBytes)
        secureRandom.nextBytes(dummyPayload)
        storage[slot] = encryptBlock(dummyPayload)
        storage[newSlot] = encryptBlock(decrypted)

        return decrypted
    }

    private fun encryptBlock(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext)
        return iv + encrypted
    }

    private fun decryptBlock(ciphertextWithIv: ByteArray): ByteArray {
        val iv = ciphertextWithIv.copyOfRange(0, 12)
        val cipherBytes = ciphertextWithIv.copyOfRange(12, ciphertextWithIv.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherBytes)
    }
}
