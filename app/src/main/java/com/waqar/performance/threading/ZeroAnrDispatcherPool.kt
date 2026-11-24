package com.waqar.performance.threading

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ZeroAnrDispatcherPool: Bounded Coroutine Thread Pools for 0% Main Thread Blocking.
 *
 * Configures isolated dispatchers using [limitedParallelism] to prevent thread starvation:
 * - [cryptoDispatcher]: max 4 parallel threads for heavy cryptographic hashing and VDF squaring.
 * - [ioStorageDispatcher]: max 8 parallel threads for disk I/O, SQLite/Room, and network sync.
 * - [biometricDispatcher]: max 2 parallel threads for camera frame analysis and facial embeddings.
 * - Integrated [SupervisorJob] and [CoroutineExceptionHandler] ensure zero uncaught crashes.
 */
object ZeroAnrDispatcherPool {

    private const val TAG = "ZeroAnrDispatcherPool"

    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w(TAG, "Caught unhandled coroutine exception in background pool: ${throwable.localizedMessage}")
    }

    val poolSupervisor = SupervisorJob()
    val poolScope = CoroutineScope(poolSupervisor + Dispatchers.Default + exceptionHandler)

    // Bounded dispatchers
    val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(4)
    val ioStorageDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8)
    val biometricDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)

    /**
     * Launches a cryptographic task on bounded crypto dispatcher.
     */
    fun launchCrypto(block: suspend CoroutineScope.() -> Unit) {
        poolScope.launch(cryptoDispatcher, block = block)
    }

    /**
     * Launches an I/O task on bounded storage dispatcher.
     */
    fun launchStorage(block: suspend CoroutineScope.() -> Unit) {
        poolScope.launch(ioStorageDispatcher, block = block)
    }

    /**
     * Launches a biometric inference task on bounded biometric dispatcher.
     */
    fun launchBiometric(block: suspend CoroutineScope.() -> Unit) {
        poolScope.launch(biometricDispatcher, block = block)
    }
}
