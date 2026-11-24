package com.waqar.institute.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.data.model.PortalRole
import com.example.ui.designsystem.WciColors
import com.example.ui.portal.ModuleDestination

/**
 * Dynamic Side Navigation Drawer Provider for the 100% Offline Coding, Testing & Learning Lab
 * Module (`M25_CODING_SANDBOX`), Pro Academic Suite (`M26_ACADEMIC_PRO_SUITE`),
 * Offline Fee Ledger (`M27_OFFLINE_FEE_LEDGER`), Tally Simulator (`M28_TALLY_SIMULATOR`),
 * Terms & Conditions (`M29_TERMS_AND_CONDITIONS`), and 10 Enterprise Modules (M30..M39).
 */
object AppDrawer {

    const val ROUTE_CODING_SANDBOX: String = "M25_CODING_SANDBOX"
    const val TITLE_CODING_SANDBOX_HINDI: String = "Coding, Testing & Learning Lab"
    const val SHORT_LABEL_CODING_SANDBOX: String = "Coding Lab"

    const val ROUTE_ACADEMIC_PRO_SUITE: String = "M26_ACADEMIC_PRO_SUITE"
    const val TITLE_ACADEMIC_PRO_SUITE: String = "QR+Face, Lab PC, ID PDF & Typing Arena"
    const val SHORT_LABEL_ACADEMIC_PRO_SUITE: String = "Pro Lab"

    const val ROUTE_OFFLINE_FEE_LEDGER: String = "M27_OFFLINE_FEE_LEDGER"
    const val TITLE_OFFLINE_FEE_LEDGER: String = "Offline Campus Fee Ledger"
    const val SHORT_LABEL_OFFLINE_FEE_LEDGER: String = "Fee Ledger"

    const val ROUTE_TALLY_SIMULATOR: String = "M28_TALLY_SIMULATOR"
    const val TITLE_TALLY_SIMULATOR: String = "Tally Practice Simulator"
    const val SHORT_LABEL_TALLY_SIMULATOR: String = "Tally Lab"

    const val ROUTE_TERMS_AND_CONDITIONS: String = "M29_TERMS_AND_CONDITIONS"
    const val TITLE_TERMS_AND_CONDITIONS: String = "Terms & Conditions"
    const val SHORT_LABEL_TERMS_AND_CONDITIONS: String = "Terms"

    // 10 Enterprise-Grade Modules (Priority 4)
    const val ROUTE_ADMIN_MULTI_BRANCH: String = "M30_MULTI_BRANCH"
    const val ROUTE_ADMIN_ERP_PAYROLL: String = "M31_ERP_PAYROLL"
    const val ROUTE_ADMIN_CALLING_CRM: String = "M32_CALLING_CRM"
    const val ROUTE_ADMIN_DRM_SHIELD: String = "M33_DRM_SHIELD"

    const val ROUTE_TEACHER_LAB_CONTROL: String = "M34_LAB_CONTROL"
    const val ROUTE_TEACHER_AI_EVALUATOR: String = "M35_AI_EVALUATOR"

    const val ROUTE_STUDENT_3D_LAB: String = "M36_3D_LAB"
    const val ROUTE_STUDENT_PROJECT_SYNC: String = "M37_PROJECT_SYNC"
    const val ROUTE_STUDENT_CAREER_MATCH: String = "M38_CAREER_MATCH"

    const val ROUTE_PARENT_LIVE_TRACKING: String = "M39_LIVE_TRACKING"

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

    val offlineFeeLedgerDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_OFFLINE_FEE_LEDGER,
        title = TITLE_OFFLINE_FEE_LEDGER,
        shortLabel = SHORT_LABEL_OFFLINE_FEE_LEDGER,
        category = "Finance",
        icon = Icons.Default.AccountBalance,
        accentStart = WciColors.SuccessNeon,
        accentEnd = WciColors.ElectricCyan
    )

    val tallyPracticeSimulatorDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_TALLY_SIMULATOR,
        title = TITLE_TALLY_SIMULATOR,
        shortLabel = SHORT_LABEL_TALLY_SIMULATOR,
        category = "Practice",
        icon = Icons.Default.AccountBalance,
        accentStart = WciColors.ScannerTeal,
        accentEnd = WciColors.SuccessNeon
    )

    val termsAndConditionsDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_TERMS_AND_CONDITIONS,
        title = TITLE_TERMS_AND_CONDITIONS,
        shortLabel = SHORT_LABEL_TERMS_AND_CONDITIONS,
        category = "Legal",
        icon = Icons.Default.Gavel,
        accentStart = WciColors.Info,
        accentEnd = WciColors.IndigoDeep
    )

    // Admin Enterprise Command Center destinations
    val adminMultiBranchDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_ADMIN_MULTI_BRANCH,
        title = "Multi-Branch & White-Label SaaS",
        shortLabel = "Multi-Branch",
        category = "Enterprise Command Center",
        icon = Icons.Default.Hub,
        accentStart = WciColors.PurpleVibrant,
        accentEnd = WciColors.accentCyan
    )

    val adminErpPayrollDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_ADMIN_ERP_PAYROLL,
        title = "Full ERP, Inventory & Payroll",
        shortLabel = "ERP & Payroll",
        category = "Enterprise Command Center",
        icon = Icons.Default.Payments,
        accentStart = WciColors.SuccessNeon,
        accentEnd = WciColors.accentCyan
    )

    val adminCallingCrmDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_ADMIN_CALLING_CRM,
        title = "AI Admission & Calling CRM",
        shortLabel = "Calling CRM",
        category = "Enterprise Command Center",
        icon = Icons.Default.SupportAgent,
        accentStart = WciColors.Warning,
        accentEnd = WciColors.IndigoAccent
    )

    val adminDrmShieldDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_ADMIN_DRM_SHIELD,
        title = "Anti-Piracy DRM & RASP Shield",
        shortLabel = "DRM Shield",
        category = "Enterprise Command Center",
        icon = Icons.Default.Security,
        accentStart = WciColors.Danger,
        accentEnd = WciColors.PurpleVibrant
    )

    // Teacher Smart Lab & AI Grader destinations
    val teacherLabControlDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_TEACHER_LAB_CONTROL,
        title = "Computer Lab Live Monitoring",
        shortLabel = "Lab Control",
        category = "Smart Lab & AI Grader",
        icon = Icons.Default.Computer,
        accentStart = WciColors.accentCyan,
        accentEnd = WciColors.Success
    )

    val teacherAiEvaluatorDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_TEACHER_AI_EVALUATOR,
        title = "AI Practical Assignment Evaluator",
        shortLabel = "AI Grader",
        category = "Smart Lab & AI Grader",
        icon = Icons.Default.AutoAwesome,
        accentStart = WciColors.IndigoAccent,
        accentEnd = WciColors.SuccessNeon
    )

    // Student Virtual Lab & Career Hub destinations
    val student3dLabDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_STUDENT_3D_LAB,
        title = "3D Hardware & Networking Lab",
        shortLabel = "3D Lab",
        category = "Virtual Lab & Career Hub",
        icon = Icons.Default.ViewInAr,
        accentStart = WciColors.accentCyan,
        accentEnd = WciColors.SuccessNeon
    )

    val studentProjectSyncDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_STUDENT_PROJECT_SYNC,
        title = "Omni-Channel Project Sync",
        shortLabel = "Project Sync",
        category = "Virtual Lab & Career Hub",
        icon = Icons.Default.CloudSync,
        accentStart = WciColors.Info,
        accentEnd = WciColors.accentCyan
    )

    val studentCareerMatchDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_STUDENT_CAREER_MATCH,
        title = "AI Career & Internship Matchmaking",
        shortLabel = "Career Hub",
        category = "Virtual Lab & Career Hub",
        icon = Icons.Default.Work,
        accentStart = WciColors.Success,
        accentEnd = WciColors.IndigoAccent
    )

    // Parent Live Ward Monitor destination
    val parentLiveTrackingDestination: ModuleDestination = ModuleDestination(
        routeId = ROUTE_PARENT_LIVE_TRACKING,
        title = "Real-Time Campus & Lab Tracking",
        shortLabel = "Live Monitor",
        category = "Live Ward Monitor",
        icon = Icons.Default.Timeline,
        accentStart = WciColors.SuccessNeon,
        accentEnd = WciColors.accentCyan
    )

    val supportedRoles: Set<PortalRole> = setOf(
        PortalRole.ADMIN,
        PortalRole.TEACHER,
        PortalRole.STUDENT,
        PortalRole.PARENT
    )

    fun isSupportedForRole(role: PortalRole): Boolean = role in supportedRoles
}
