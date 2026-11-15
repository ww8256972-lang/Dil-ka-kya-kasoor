package com.waqar.institute.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.Color
import com.example.data.model.PortalRole
import com.example.ui.portal.ModuleDestination

/**
 * Dynamic Side Navigation Drawer Provider for the 100% Offline Coding, Testing & Learning Skill
 * Module (`AppDrawer.kt`).
 *
 * Injects the `"कोडिंग, टेस्टिंग और लर्निंग स्किल"` (`M25_CODING_SANDBOX`) route across all four
 * portals (Admin, Teacher, Student, and Parent) without removing or altering any existing modules.
 */
object AppDrawer {

    const val ROUTE_CODING_SANDBOX: String = "M25_CODING_SANDBOX"
    const val TITLE_CODING_SANDBOX_HINDI: String = "कोडिंग, टेस्टिंग और लर्निंग स्किल"
    const val SHORT_LABEL_CODING_SANDBOX: String = "Coding Lab"

    val codingSandboxDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_CODING_SANDBOX,
        title = TITLE_CODING_SANDBOX_HINDI,
        shortLabel = SHORT_LABEL_CODING_SANDBOX,
        category = "Academics",
        icon = Icons.Default.Terminal,
        accentStart = Color(0xFF10F596),
        accentEnd = Color(0xFF00E5FF)
    )

    val supportedRoles: Set<PortalRole> = setOf(
        PortalRole.ADMIN,
        PortalRole.TEACHER,
        PortalRole.STUDENT,
        PortalRole.PARENT
    )

    fun isSupportedForRole(role: PortalRole): Boolean = role in supportedRoles
}
