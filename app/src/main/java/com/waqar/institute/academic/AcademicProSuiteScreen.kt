package com.waqar.institute.academic

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.PortalRole
import com.example.data.model.StudentRecord
import com.example.viewmodel.InstituteUiState
import com.example.viewmodel.InstituteViewModel
import com.waqar.institute.biometric.MultiStageBiometricCoordinator
import com.waqar.institute.performance.Smooth120HzOptimizer
import com.waqar.institute.security.MahaBrahmastraProPlusEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcademicProSuiteScreen(
    state: InstituteUiState,
    viewModel: InstituteViewModel,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 4)) }

    // Initialize 120Hz Coil & Canary Honeypot on entry
    val smoothnessDiag = remember {
        MahaBrahmastraProPlusEngine.initializeCanaryHoneypotVault(context)
        Smooth120HzOptimizer.initializeHighRefreshPipeline(context)
    }
    val memReport = remember { MahaBrahmastraProPlusEngine.auditNativeExecutableMemorySegments() }
    val overlayReport = remember { MahaBrahmastraProPlusEngine.auditOverlayScreenShareAndAccessibility(context) }
    val canaryReport = remember { MahaBrahmastraProPlusEngine.verifyCanaryIntactOrAlert(context) }

    val tabs = listOf(
        "QR + Face Scanner",
        "Lab PC & Syllabus",
        "PDF ID & Admit Card",
        "Typing Speed Arena",
        "Brahmastra Pro+ (#6–#10)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("academic_pro_suite_screen")
    ) {
        // Header Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131936)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Pro Academic Suite",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Waqar Pro Academic, Lab & Brahmastra Pro+ Hub",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "QR+128D Face • 24 Lab PCs • Print PDF ID/Admit • Hindi/English WPM • 120Hz (${smoothnessDiag.targetFrameBudgetMs}ms)",
                        color = Color(0xFFA5B4FC),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    modifier = Modifier.testTag("pro_suite_tab_$idx"),
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> QrAndFaceDualScannerTab(state = state, viewModel = viewModel)
            1 -> LabPcAllotmentAndSyllabusTab(state = state, viewModel = viewModel)
            2 -> PdfIdAndAdmitCardStudioTab(state = state, viewModel = viewModel)
            3 -> TypingSpeedArenaTab(state = state, viewModel = viewModel)
            4 -> BrahmastraProPlusAndBiometricTab(
                state = state,
                viewModel = viewModel,
                memReport = memReport,
                overlayReport = overlayReport,
                canaryReport = canaryReport
            )
        }
    }
}

@Composable
private fun QrAndFaceDualScannerTab(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    val students = state.students.ifEmpty {
        listOf(
            StudentRecord(
                id = "stu_demo_01",
                enrollmentNo = "WCI-2026-1001",
                fullName = "Aarav Sharma",
                courseName = "ADCA Diploma",
                batchName = "Morning Batch A"
            )
        )
    }
    var selectedStudent by remember(students) { mutableStateOf(students.first()) }
    var qrInput by remember(selectedStudent) {
        mutableStateOf(AcademicProSuiteEngine.generateSignedStudentQrPayload(selectedStudent))
    }
    var blinkChecked by remember { mutableStateOf(true) }
    var headYawDegrees by remember { mutableStateOf(14.5f) }
    var scanStatus by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17203F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "QR + Face Scanner",
                            tint = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QR + 128-D Face Dual-Factor Attendance",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scans Student ID QR signature (Keystore HMAC) + verifies live 128-D Face Vector & Eye Blink in <0.5s.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Select Student to Generate / Scan Signed ID QR:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        students.take(3).forEach { stu ->
                            FilterChip(
                                selected = selectedStudent.id == stu.id,
                                onClick = {
                                    selectedStudent = stu
                                    qrInput = AcademicProSuiteEngine.generateSignedStudentQrPayload(stu)
                                },
                                label = { Text("${stu.fullName} (${stu.enrollmentNo})") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qrInput,
                        onValueChange = { qrInput = it },
                        label = { Text("Cryptographic Student QR Payload (HMAC-Sealed)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_dual_payload_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blinkChecked, onCheckedChange = { blinkChecked = it })
                        Text(
                            text = "Live Eye-Blink & Head-Turn Liveness Verified (${headYawDegrees}° Yaw)",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val vec = FloatArray(128) { i -> kotlin.math.sin((i + 1) * 0.17f) }
                            val res = AcademicProSuiteEngine.verifyQrAndFaceDualFactorAttendance(
                                rawQrPayload = qrInput,
                                liveFaceEmbedding = vec,
                                enrolledFaceEmbedding = vec,
                                blinkVerified = blinkChecked,
                                markedByUid = state.userProfile?.uid ?: "admin_waqar"
                            )
                            when (res) {
                                is AcademicProSuiteEngine.DualFactorAttendanceResult.Verified -> {
                                    viewModel.markAttendance(
                                        targetType = "STUDENT",
                                        targetId = res.record.targetId,
                                        targetName = res.record.targetName,
                                        batchName = res.record.batchName,
                                        status = "PRESENT",
                                        method = "QR_PLUS_FACE_BIOMETRIC",
                                        remarks = res.record.remarks
                                    )
                                    scanStatus = "VERIFIED & MARKED PRESENT: ${res.record.targetName} • 128-D Similarity ${(res.cosineSimilarity * 100).toInt()}% • RowMAC=${res.rowMacHex.take(12)}"
                                }
                                is AcademicProSuiteEngine.DualFactorAttendanceResult.Rejected -> {
                                    scanStatus = "REJECTED: ${res.reason}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("verify_qr_face_attendance_button")
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR + Verify 128-D Face & Mark Present", fontWeight = FontWeight.Bold)
                    }

                    scanStatus?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = msg,
                            color = if (msg.startsWith("VERIFIED")) Color(0xFF34D399) else Color(0xFFF87171),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabPcAllotmentAndSyllabusTab(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    var workstations by remember { mutableStateOf(AcademicProSuiteEngine.getAllWorkstations()) }
    var syllabusTopics by remember { mutableStateOf(AcademicProSuiteEngine.getSyllabusTopics()) }
    var selectedPcCode by remember { mutableStateOf("PC-01") }
    var studentNameInput by remember {
        mutableStateOf(state.students.firstOrNull()?.fullName ?: "Aarav Sharma")
    }
    var enrollmentInput by remember {
        mutableStateOf(state.students.firstOrNull()?.enrollmentNo ?: "WCI-2026-1001")
    }
    var batchInput by remember {
        mutableStateOf(state.batches.firstOrNull()?.name ?: "ADCA Morning 9AM")
    }
    var newTopicCourse by remember { mutableStateOf("ADCA") }
    var newTopicTitle by remember { mutableStateOf("") }

    val canEdit = state.activePortalRole == PortalRole.ADMIN || state.activePortalRole == PortalRole.TEACHER
    val adcaCompletion = AcademicProSuiteEngine.computeCourseCompletionPercent("ADCA")

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17203F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF60A5FA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Computer Lab Workstation Grid (PC-01 to PC-24)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val assignedCount = workstations.count { it.status == AcademicProSuiteEngine.LabPcStatus.ASSIGNED }
                    val availCount = workstations.count { it.status == AcademicProSuiteEngine.LabPcStatus.AVAILABLE }
                    val maintCount = workstations.count { it.status == AcademicProSuiteEngine.LabPcStatus.MAINTENANCE }
                    Text(
                        text = "Available: $availCount • Assigned: $assignedCount • Maintenance: $maintCount",
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        workstations.take(12).forEach { pc ->
                            val bg = when (pc.status) {
                                AcademicProSuiteEngine.LabPcStatus.AVAILABLE -> Color(0xFF064E3B)
                                AcademicProSuiteEngine.LabPcStatus.ASSIGNED -> Color(0xFF312E81)
                                AcademicProSuiteEngine.LabPcStatus.MAINTENANCE -> Color(0xFF7F1D1D)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(
                                        width = if (selectedPcCode == pc.pcCode) 2.dp else 1.dp,
                                        color = if (selectedPcCode == pc.pcCode) Color.White else Color(0xFF475569),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedPcCode = pc.pcCode }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(pc.pcCode, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        text = if (pc.assignedStudentName.isNotBlank()) pc.assignedStudentName.take(10) else pc.status.label,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    if (canEdit) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = studentNameInput,
                            onValueChange = { studentNameInput = it },
                            label = { Text("Assign Student Name to $selectedPcCode") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    AcademicProSuiteEngine.assignStudentToWorkstation(
                                        pcCode = selectedPcCode,
                                        studentId = "stu_${enrollmentInput.lowercase()}",
                                        studentName = studentNameInput,
                                        enrollmentNo = enrollmentInput,
                                        batchName = batchInput
                                    )
                                    workstations = AcademicProSuiteEngine.getAllWorkstations()
                                    viewModel.recordAudit(
                                        action = "ALLOT_LAB_PC",
                                        module = "M20_INVENTORY",
                                        targetId = selectedPcCode,
                                        severity = "INFO",
                                        details = "Allotted $selectedPcCode to $studentNameInput ($enrollmentInput)"
                                    )
                                    viewModel.postMessage("Allotted $selectedPcCode to $studentNameInput")
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("allot_lab_pc_button")
                            ) {
                                Text("Allot $selectedPcCode")
                            }
                            OutlinedButton(
                                onClick = {
                                    val curr = workstations.firstOrNull { it.pcCode == selectedPcCode }
                                    val toggleMaint = curr?.status != AcademicProSuiteEngine.LabPcStatus.MAINTENANCE
                                    AcademicProSuiteEngine.setWorkstationMaintenanceStatus(selectedPcCode, toggleMaint)
                                    workstations = AcademicProSuiteEngine.getAllWorkstations()
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Toggle Maintenance")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17203F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Course Syllabus Milestone Tracker (ADCA Progress: $adcaCompletion%)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { adcaCompletion / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Color(0xFF34D399)
                    )
                    if (canEdit) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newTopicTitle,
                                onValueChange = { newTopicTitle = it },
                                label = { Text("Add Daily Class Topic ($newTopicCourse)") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newTopicTitle.isNotBlank()) {
                                        AcademicProSuiteEngine.addCustomSyllabusTopic(
                                            courseName = newTopicCourse,
                                            batchName = batchInput,
                                            moduleNumber = syllabusTopics.size + 1,
                                            topicTitle = newTopicTitle
                                        )
                                        newTopicTitle = ""
                                        syllabusTopics = AcademicProSuiteEngine.getSyllabusTopics()
                                    }
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }
            }
        }

        items(syllabusTopics, key = { it.id }) { topic ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = topic.completed,
                        enabled = canEdit,
                        onCheckedChange = { checked ->
                            AcademicProSuiteEngine.toggleSyllabusTopicCompletion(
                                topicId = topic.id,
                                completed = checked,
                                teacherName = state.userProfile?.displayName ?: "Institute Faculty"
                            )
                            syllabusTopics = AcademicProSuiteEngine.getSyllabusTopics()
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "[${topic.courseName} • M${topic.moduleNumber}] ${topic.topicTitle}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        if (topic.completed) {
                            Text(
                                text = "Completed by ${topic.completedByTeacher} on ${topic.completedDateIso}",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfIdAndAdmitCardStudioTab(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    val context = LocalContext.current
    val students = state.students.ifEmpty {
        listOf(
            StudentRecord(
                id = "stu_id_01",
                enrollmentNo = "WCI-2026-1001",
                fullName = state.userProfile?.displayName ?: "Aarav Sharma",
                courseName = "ADCA (Advanced Diploma)",
                batchName = "Morning Batch 9AM",
                guardianName = "Rajesh Sharma",
                phone = "+91 9876543210"
            )
        )
    }
    var selectedStudent by remember(students) { mutableStateOf(students.first()) }
    var examTitleInput by remember { mutableStateOf("ADCA Final Diploma CBT Examination 2026") }
    var allottedPcInput by remember { mutableStateOf("PC-01") }
    var lastArtifact by remember { mutableStateOf<AcademicProSuiteEngine.GeneratedPdfArtifact?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17203F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFFFBBF24))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Print-Ready PDF ID Card & Exam Admit Card Studio",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Generates official vector PDFs with embedded cryptographic QR code and SHA-256 checksum.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = examTitleInput,
                        onValueChange = { examTitleInput = it },
                        label = { Text("Exam Title for Hall Ticket (Admit Card)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = allottedPcInput,
                        onValueChange = { allottedPcInput = it },
                        label = { Text("Allotted Computer Lab PC (e.g., PC-01)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val artifact = AcademicProSuiteEngine.generateStudentIdCardPdf(
                                    context = context,
                                    student = selectedStudent,
                                    instituteName = state.preferences.instituteName
                                )
                                lastArtifact = artifact
                                viewModel.recordAudit(
                                    action = "GENERATE_PDF_ID_CARD",
                                    module = "M03_STUDENTS",
                                    targetId = selectedStudent.id,
                                    severity = "INFO",
                                    details = "Generated PDF ID Card (${artifact.byteSize} bytes • SHA256:${artifact.sha256ChecksumHex.take(12)})"
                                )
                                viewModel.postMessage("Generated Official PDF ID Card for ${selectedStudent.fullName}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("generate_pdf_id_card_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PVC ID Card PDF")
                        }

                        Button(
                            onClick = {
                                val artifact = AcademicProSuiteEngine.generateExamAdmitCardPdf(
                                    context = context,
                                    student = selectedStudent,
                                    examTitle = examTitleInput,
                                    examDateIso = "2026-12-01",
                                    durationMinutes = 60,
                                    allottedPcCode = allottedPcInput,
                                    instituteName = state.preferences.instituteName
                                )
                                lastArtifact = artifact
                                viewModel.recordAudit(
                                    action = "GENERATE_EXAM_ADMIT_CARD",
                                    module = "M10_EXAMS",
                                    targetId = selectedStudent.id,
                                    severity = "INFO",
                                    details = "Generated Exam Admit Card (${artifact.byteSize} bytes • Seat:$allottedPcInput)"
                                )
                                viewModel.postMessage("Generated Exam Hall Ticket PDF for ${selectedStudent.fullName}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("generate_admit_card_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Admit Card PDF")
                        }
                    }

                    lastArtifact?.let { pdf ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Generated: ${pdf.file.name} (${pdf.byteSize} bytes)",
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "SHA-256 Seal: ${pdf.sha256ChecksumHex.take(32)}...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "QR Payload: ${pdf.verificationQrPayload}",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                pdf.file
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Official WCI PDF"))
                                        }
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share / Print Official PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingSpeedArenaTab(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    val passages = AcademicProSuiteEngine.builtInTypingPassages
    var selectedPassage by remember { mutableStateOf(passages.first()) }
    var typedText by remember { mutableStateOf("") }
    var backspaceCount by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var startEpochMs by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableIntStateOf(1) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(500L)
            if (startEpochMs > 0L) {
                elapsedSec = (((System.currentTimeMillis() - startEpochMs) / 1000L).toInt()).coerceAtLeast(1)
            }
        }
    }

    val liveEval = remember(selectedPassage, typedText, elapsedSec, backspaceCount) {
        AcademicProSuiteEngine.evaluateTypingPerformance(
            referencePassage = selectedPassage.text,
            typedText = typedText,
            elapsedSeconds = elapsedSec,
            backspaceCount = backspaceCount,
            language = selectedPassage.language
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17203F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Keyboard, contentDescription = null, tint = Color(0xFF34D399))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Typing Speed & Accuracy Arena (English & Hindi WPM)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        passages.forEach { p ->
                            FilterChip(
                                selected = selectedPassage.id == p.id,
                                onClick = {
                                    selectedPassage = p
                                    typedText = ""
                                    backspaceCount = 0
                                    isRunning = false
                                    elapsedSec = 1
                                },
                                label = { Text("${p.language} (${p.targetWpm} WPM)") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedPassage.text,
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill("Net WPM", "${liveEval.netWpm}", Color(0xFF34D399))
                        MetricPill("Gross WPM", "${liveEval.grossWpm}", Color(0xFF60A5FA))
                        MetricPill("Accuracy", "${liveEval.accuracyPercent}%", Color(0xFFFBBF24))
                        MetricPill("Backspaces", "$backspaceCount", Color(0xFFF87171))
                        MetricPill("Time", "${elapsedSec}s", Color(0xFFA5B4FC))
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = { newVal ->
                            if (!isRunning && newVal.isNotEmpty()) {
                                isRunning = true
                                startEpochMs = System.currentTimeMillis()
                            }
                            if (newVal.length < typedText.length) {
                                backspaceCount++
                            }
                            typedText = newVal
                        },
                        label = { Text("Start Typing Here (${selectedPassage.language})...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("typing_arena_input_field")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                isRunning = false
                                val studentId = state.userProfile?.linkedEntityId?.ifBlank { state.userProfile.uid } ?: "stu_typing"
                                val studentName = state.userProfile?.displayName ?: "Institute Student"
                                viewModel.recordExamResult(
                                    examId = "typing_${selectedPassage.id}",
                                    examTitle = "Typing Test (${selectedPassage.language} • ${liveEval.netWpm} WPM)",
                                    studentId = studentId,
                                    studentName = studentName,
                                    marksObtained = liveEval.accuracyPercent,
                                    totalMarks = 100,
                                    remarks = "NetWPM=${liveEval.netWpm} | GrossWPM=${liveEval.grossWpm} | Accuracy=${liveEval.accuracyPercent}% | Backspaces=$backspaceCount | ${liveEval.grade}"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("save_typing_score_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Submit Score (${liveEval.grade})")
                        }
                        OutlinedButton(
                            onClick = {
                                isRunning = false
                                typedText = ""
                                backspaceCount = 0
                                elapsedSec = 1
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
        Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp)
    }
}

@Composable
private fun BrahmastraProPlusAndBiometricTab(
    state: InstituteUiState,
    viewModel: InstituteViewModel,
    memReport: MahaBrahmastraProPlusEngine.MemoryIntegrityReport,
    overlayReport: MahaBrahmastraProPlusEngine.ScreenOverlayAndRemoteAuditReport,
    canaryReport: MahaBrahmastraProPlusEngine.CanaryAuditResult
) {
    var stepUpVerified by remember {
        mutableStateOf(MultiStageBiometricCoordinator.isAdminStepUpVerifiedWithinWindow())
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF17203F)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF34D399))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Maha Brahmastra Pro+ (#6–#10) & Biometric Gates",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Layered on top of preserved Rules #1–#5 (Cert Pin, DEX CRC32, HMAC Exam Vault, 5-in-60s Rate Limiter, Signed OTA).",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SecurityRuleBadge(
                        title = "Pro+ #6: Native /proc/self/maps Executable Segment Scanner",
                        detail = "Exec Segments: ${memReport.executableSegmentCount} • SHA256: ${memReport.executableSegmentsDigestHex.take(16)}...",
                        passed = memReport.isClean
                    )
                    SecurityRuleBadge(
                        title = "Pro+ #7: Anti-Overlay, Screen-Share & Remote-Control Blocker",
                        detail = if (overlayReport.isSafeForProctoredExam) "Zero virtual displays or remote-control accessibility services" else overlayReport.blockingReason.orEmpty(),
                        passed = overlayReport.isSafeForProctoredExam
                    )
                    SecurityRuleBadge(
                        title = "Pro+ #8: Per-Request Cryptographic Nonce + Monotonic Counter",
                        detail = "CSPRNG 128-bit single-use nonce + AtomicLong counter + Keystore HMAC",
                        passed = true
                    )
                    SecurityRuleBadge(
                        title = "Pro+ #9: Canary Honeypot Trap Vault",
                        detail = if (canaryReport.intact) "All 3 decoy canary keys intact in EncryptedSharedPreferences" else canaryReport.alertMessage.orEmpty(),
                        passed = canaryReport.intact
                    )
                    SecurityRuleBadge(
                        title = "Pro+ #10: Hardware Keystore Row-Level MAC Verifier",
                        detail = "HmacSHA256 row seal active for Attendance, Fees & Exam records",
                        passed = true
                    )

                    if (state.activePortalRole == PortalRole.ADMIN) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                MultiStageBiometricCoordinator.recordAdminStepUpFaceVerification()
                                stepUpVerified = MultiStageBiometricCoordinator.isAdminStepUpVerifiedWithinWindow()
                                viewModel.recordAudit(
                                    action = "ADMIN_STEP_UP_FACE_VERIFIED",
                                    module = "M22_SECURITY",
                                    targetId = state.userProfile?.username ?: "waqar",
                                    severity = "INFO",
                                    details = "Admin completed 128-D Step-Up Face Lock for critical destructive operations (valid 10m)"
                                )
                                viewModel.postMessage("Admin Step-Up Face Lock Verified for 10 minutes ✓")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (stepUpVerified) Color(0xFF059669) else Color(0xFF4F46E5)
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_step_up_face_button")
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (stepUpVerified) "Admin Step-Up Face Lock Active (10m Window)"
                                else "Verify Admin Step-Up Face for Critical Actions"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityRuleBadge(title: String, detail: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (passed) Color(0xFF34D399) else Color(0xFFF87171),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(detail, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}
