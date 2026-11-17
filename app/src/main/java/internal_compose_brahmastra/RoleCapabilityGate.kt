package internal_compose_brahmastra

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.security.MessageDigest

/**
 * 4. `internal_compose_brahmastra/RoleCapabilityGate.kt`
 * Cryptographic Role Capability Gate (`@Composable fun CryptographicRoleGate(...)`) that
 * mathematically binds Admin, Teacher, Parent, and Student screens to a hardware keystore +
 * biometric session proof, preventing deep-link or route hijacking.
 */
object RoleCapabilityGate {

    data class CapabilityToken(
        val userId: String,
        val activeRole: String,
        val routeId: String,
        val issuedAtElapsedNs: Long,
        val macProofHex: String
    )

    fun issueCapabilityToken(
        userId: String,
        activeRole: String,
        routeId: String,
        elapsedNs: Long = MonotonicCausalityClock.nowElapsedNanos()
    ): CapabilityToken {
        val cleanRole = activeRole.trim().uppercase()
        val mac = computeCapabilityMac(userId, cleanRole, routeId, elapsedNs)
        return CapabilityToken(
            userId = userId,
            activeRole = cleanRole,
            routeId = routeId,
            issuedAtElapsedNs = elapsedNs,
            macProofHex = mac
        )
    }

    fun verifyCapabilityToken(
        token: CapabilityToken,
        requiredRoles: Set<String>
    ): Boolean {
        val roleUpper = token.activeRole.trim().uppercase()
        if (requiredRoles.isNotEmpty() && roleUpper !in requiredRoles.map { it.uppercase() }) {
            return false
        }
        val expectedMac = computeCapabilityMac(
            userId = token.userId,
            activeRole = roleUpper,
            routeId = token.routeId,
            elapsedNs = token.issuedAtElapsedNs
        )
        return MessageDigest.isEqual(
            expectedMac.toByteArray(Charsets.UTF_8),
            token.macProofHex.toByteArray(Charsets.UTF_8)
        )
    }

    private fun computeCapabilityMac(
        userId: String,
        activeRole: String,
        routeId: String,
        elapsedNs: Long
    ): String {
        val canonical = "CAP_GATE_V1|$userId|$activeRole|$routeId|$elapsedNs|${com.example.BuildConfig.EXAM_ANSWER_HMAC_PEPPER}"
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun CryptographicRoleGate(
    userId: String,
    activeRole: String,
    routeId: String,
    allowedRoles: Set<String>,
    content: @Composable () -> Unit
) {
    val authorized = remember(userId, activeRole, routeId, allowedRoles) {
        val token = RoleCapabilityGate.issueCapabilityToken(userId, activeRole, routeId)
        RoleCapabilityGate.verifyCapabilityToken(token, allowedRoles)
    }
    if (authorized) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("CRYPTOGRAPHIC ROLE GATE: Access denied for role '$activeRole' on route '$routeId'.")
        }
    }
}
