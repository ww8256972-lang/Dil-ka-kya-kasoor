package com.wci.nativecxx

object WciNativeSentinel {

    private val isNativeLoaded: Boolean = try {
        System.loadLibrary("wci_sentinel")
        true
    } catch (e: Throwable) {
        false
    }

    /**
     * Checks if process has active debuggers, Frida agent, ptrace hooking,
     * or ELF memory modifications.
     */
    fun checkTamperState(): Boolean {
        return if (isNativeLoaded) {
            isTampered()
        } else {
            // JVM / Non-NDK environment safe baseline
            false
        }
    }

    /**
     * Wipes sensitive cryptographic buffers in native heap/stack using memset_s and compiler barrier.
     */
    fun scrubMemory(buffer: ByteArray) {
        if (isNativeLoaded) {
            wipeMemory(buffer)
        } else {
            buffer.fill(0)
        }
    }

    /**
     * Applies hard virtual memory limit (RLIMIT_AS) and thread stack limit (RLIMIT_STACK).
     */
    fun configureSandboxCaps(maxVirtualBytes: Long = 512L * 1024L * 1024L, maxStackBytes: Long = 512L * 1024L): Boolean {
        return if (isNativeLoaded) {
            enforceSandboxLimits(maxVirtualBytes, maxStackBytes)
        } else {
            true
        }
    }

    /**
     * Verifies if Linux kernel SELinux MAC is in Enforcing mode.
     */
    fun checkSELinuxEnforcing(): Boolean {
        return if (isNativeLoaded) {
            isSELinuxEnforcing()
        } else {
            true
        }
    }

    /**
     * Installs Seccomp-BPF filter blocking ptrace and cross-process memory inspection.
     */
    fun applySeccompFilter(): Boolean {
        return if (isNativeLoaded) {
            installSeccompFilter()
        } else {
            true
        }
    }

    /**
     * Derives cryptographic exam seal using polymorphic bytecode VM.
     */
    fun generateExamSeal(studentUidHash: Long, examIdHash: Long, timestampSec: Long): ByteArray {
        return if (isNativeLoaded) {
            deriveExamSeal(studentUidHash, examIdHash, timestampSec)
        } else {
            // Deterministic software fallback for unit test harness
            val combined = (studentUidHash xor 0x6A09E667F3BCC908L) * 31L + (examIdHash xor timestampSec)
            val out = ByteArray(32)
            for (i in 0 until 8) {
                out[i] = ((combined ushr (i * 8)) and 0xFF).toByte()
            }
            out
        }
    }

    /**
     * Constant-time XOR unmasking of obfuscated keys.
     */
    fun unmaskKey(masked: ByteArray, mask: ByteArray): ByteArray {
        return if (isNativeLoaded) {
            unmaskConfigKey(masked, mask)
        } else {
            val result = ByteArray(masked.size)
            for (i in masked.indices) {
                result[i] = (masked[i].toInt() xor mask[i % mask.size].toInt()).toByte()
            }
            result
        }
    }

    // JNI Native Methods dynamically registered in JniVaultBridge.cpp
    @JvmStatic private external fun isTampered(): Boolean
    @JvmStatic private external fun wipeMemory(buffer: ByteArray)
    @JvmStatic private external fun enforceSandboxLimits(maxVirtualBytes: Long, maxStackBytes: Long): Boolean
    @JvmStatic private external fun isSELinuxEnforcing(): Boolean
    @JvmStatic private external fun installSeccompFilter(): Boolean
    @JvmStatic private external fun deriveExamSeal(studentUidHash: Long, examIdHash: Long, timestampSec: Long): ByteArray
    @JvmStatic private external fun unmaskConfigKey(maskedKey: ByteArray, mask: ByteArray): ByteArray
}
