package com.wci.sandbox.runtime

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * WatchdogTimer (S-40)
 * Enforces a strict 3,000 millisecond execution deadline on student code.
 */
object WatchdogTimer {

    const val TIMEOUT_MS = 3000L

    suspend fun <T> runWithWatchdog(block: suspend () -> T): Result<T> {
        return try {
            val result = withTimeout(TIMEOUT_MS) {
                block()
            }
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Result.failure(TimeoutException("Execution Time Limit Exceeded (3.0s Timeout)"))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

class TimeoutException(message: String) : RuntimeException(message)
