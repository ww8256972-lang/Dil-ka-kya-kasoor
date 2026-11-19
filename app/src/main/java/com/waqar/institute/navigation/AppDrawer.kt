package com.waqar.institute.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import com.example.data.model.PortalRole
import com.example.ui.designsystem.WciColors
import com.example.ui.portal.ModuleDestination

/**
 * Dynamic Side Navigation Drawer Provider for the 100% Offline Coding, Testing & Learning Lab
 * Module (`M25_CODING_SANDBOX`) and the Pro Academic, Lab, PDF ID & Typing Suite (`M26_ACADEMIC_PRO_SUITE`).
 */
object AppDrawer {

    const val ROUTE_CODING_SANDBOX: String = "M25_CODING_SANDBOX"
    const val TITLE_CODING_SANDBOX_HINDI: String = "Coding, Testing & Learning Lab"
    const val SHORT_LABEL_CODING_SANDBOX: String = "Coding Lab"

    const val ROUTE_ACADEMIC_PRO_SUITE: String = "M26_ACADEMIC_PRO_SUITE"
    const val TITLE_ACADEMIC_PRO_SUITE: String = "QR+Face, Lab PC, ID PDF & Typing Arena"
    const val SHORT_LABEL_ACADEMIC_PRO_SUITE: String = "Pro Lab"

    const val ROUTE_UPI_EMI_COMMAND: String = "M27_UPI_EMI_COMMAND"
    const val TITLE_UPI_EMI_COMMAND: String = "Zero-Fee UPI & Auto-EMI Vault"
    const val SHORT_LABEL_UPI_EMI_COMMAND: String = "UPI & EMI"

    val codingSandboxDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_CODING_SANDBOX,
        title = TITLE_CODING_SANDBOX_HINDI,
        shortLabel = SHORT_LABEL_CODING_SANDBOX,
        category = "Academics",
        icon = Icons.Default.Terminal,
        accentStart = WciColors.SuccessNeon,
        accentEnd = WciColors.Info
    )

    val academicProSuiteDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_ACADEMIC_PRO_SUITE,
        title = TITLE_ACADEMIC_PRO_SUITE,
        shortLabel = SHORT_LABEL_ACADEMIC_PRO_SUITE,
        category = "Academics",
        icon = Icons.Default.VerifiedUser,
        accentStart = WciColors.Info,
        accentEnd = WciColors.SuccessNeon
    )

    val upiAutoEmiVaultDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_UPI_EMI_COMMAND,
        title = TITLE_UPI_EMI_COMMAND,
        shortLabel = SHORT_LABEL_UPI_EMI_COMMAND,
        category = "Finance",
        icon = Icons.Default.VerifiedUser,
        accentStart = WciColors.SuccessNeon,
        accentEnd = WciColors.PurpleVibrant
    )

    val supportedRoles: Set<PortalRole> = setOf(
        PortalRole.ADMIN,
        PortalRole.TEACHER,
        PortalRole.STUDENT,
        PortalRole.PARENT
    )

    fun isSupportedForRole(role: PortalRole): Boolean = role in supportedRoles
}
