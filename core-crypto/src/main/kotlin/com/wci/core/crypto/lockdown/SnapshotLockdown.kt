package com.wci.core.crypto.lockdown

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.wci.nativecxx.WciNativeSentinel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CampusLockdownState(
    val isLockdownActive: Boolean = false,
    val reason: String = "",
    val triggeredByAdminUid: String = "",
    val timestampMs: Long = 0L
)

/**
 * SnapshotLockdown (S-45)
 * Subscribes to real-time Firestore onSnapshot security broadcasts for WCI_UNNAO_MAIN.
 * Immediately purges volatile memory and invokes emergency shutdown upon active lockdown signal.
 */
class SnapshotLockdown(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val campusId: String = "WCI_UNNAO_MAIN"
) {
    private var listenerRegistration: ListenerRegistration? = null
    private val _lockdownState = MutableStateFlow(CampusLockdownState())
    val lockdownState: StateFlow<CampusLockdownState> = _lockdownState.asStateFlow()

    private val onLockdownTriggeredCallbacks = mutableListOf<() -> Unit>()

    /**
     * Registers a callback executed immediately when a lockdown signal is received.
     */
    fun addLockdownListener(callback: () -> Unit) {
        synchronized(onLockdownTriggeredCallbacks) {
            onLockdownTriggeredCallbacks.add(callback)
        }
    }

    /**
     * Starts listening to Firestore lockdown document.
     */
    fun startListening() {
        stopListening()
        val docRef = firestore.collection("campuses")
            .document(campusId)
            .collection("security_lockdown")
            .document("active_state")

        listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                return@addSnapshotListener
            }
            handleSnapshot(snapshot)
        }
    }

    private fun handleSnapshot(snapshot: DocumentSnapshot) {
        val active = snapshot.getBoolean("lockdownActive") ?: false
        val reason = snapshot.getString("reason") ?: "Administrative Emergency Lockdown"
        val adminUid = snapshot.getString("triggeredByAdminUid") ?: ""
        val timestamp = snapshot.getLong("timestampMs") ?: System.currentTimeMillis()

        val newState = CampusLockdownState(
            isLockdownActive = active,
            reason = reason,
            triggeredByAdminUid = adminUid,
            timestampMs = timestamp
        )

        _lockdownState.value = newState

        if (active) {
            // S-45: Zero memory immediately
            executeVolatileMemorySanitization()
            synchronized(onLockdownTriggeredCallbacks) {
                onLockdownTriggeredCallbacks.forEach { it.invoke() }
            }
        }
    }

    private fun executeVolatileMemorySanitization() {
        // Create scratch scrubber buffer to force CPU register and cache line flushing
        val flushBuffer = ByteArray(4096) { 0xFF.toByte() }
        WciNativeSentinel.scrubMemory(flushBuffer)
    }

    /**
     * Stops listening and cleans up Firestore listener.
     */
    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
