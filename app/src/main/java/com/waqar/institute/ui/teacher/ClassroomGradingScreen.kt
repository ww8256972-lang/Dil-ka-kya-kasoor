package com.waqar.institute.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.components.GlassButton
import com.wci.ui.liquidglass.components.GlassButtonVariant
import com.wci.ui.liquidglass.components.GlassCard
import com.wci.ui.liquidglass.components.GlassPill
import com.wci.ui.liquidglass.components.GlassTextField
import com.wci.ui.liquidglass.theme.GlassShaderTokens
import com.waqar.institute.data.teacher.OfflineGradeQueue
import com.waqar.institute.data.teacher.QueuedGradeEntry
import java.util.UUID

data class ClassroomStudent(
    val studentRollNo: String,
    val studentName: String,
    var practicalScore: Double = 0.0,
    var assignmentScore: Double = 0.0,
    var isPresent: Boolean = true
)

/**
 * ClassroomGradingScreen (M2.5: F-251 to F-266)
 * Classroom Attendance & Lab Practical Grading:
 * - Direct QR scan / roll-call marking
 * - Practical evaluation rubrics
 * - Offline grade queue persistence (OfflineGradeQueue)
 */
@Composable
fun ClassroomGradingScreen(
    batchId: String,
    courseId: String,
    teacherUid: String,
    students: List<ClassroomStudent> = emptyList(),
    modifier: Modifier = Modifier
) {
    var activeStudent by remember { mutableStateOf<ClassroomStudent?>(null) }
    var practicalInput by remember { mutableStateOf("") }
    var assignmentInput by remember { mutableStateOf("") }
    var remarksInput by remember { mutableStateOf("") }
    var savedSuccessMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassShaderTokens.BrandNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 20.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Classroom Lab & Practical Rubrics",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Batch: $batchId • Course: $courseId",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 12.sp
                        )
                    }

                    GlassPill(
                        text = "${students.size} STUDENTS",
                        color = GlassShaderTokens.BrandCyan
                    )
                }
            }

            // Rubric Grading Dialog Card
            if (activeStudent != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassShaderTokens.BrandMidnight,
                    contentPadding = 18.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Grading: ${activeStudent!!.studentName} (${activeStudent!!.studentRollNo})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassTextField(
                                value = practicalInput,
                                onValueChange = { practicalInput = it },
                                label = "Practical Lab (0-50)",
                                placeholder = "45",
                                modifier = Modifier.weight(1f)
                            )

                            GlassTextField(
                                value = assignmentInput,
                                onValueChange = { assignmentInput = it },
                                label = "Assignment (0-50)",
                                placeholder = "48",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        GlassTextField(
                            value = remarksInput,
                            onValueChange = { remarksInput = it },
                            label = "Faculty Rubric Remarks",
                            placeholder = "Excellent code quality & clean SQL schema design",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            GlassButton(
                                text = "Cancel",
                                onClick = { activeStudent = null },
                                variant = GlassButtonVariant.SECONDARY,
                                height = 38.dp
                            )

                            Spacer(modifier = Modifier.size(10.dp))

                            GlassButton(
                                text = "Save Grade",
                                onClick = {
                                    val entry = QueuedGradeEntry(
                                        entryId = UUID.randomUUID().toString(),
                                        campusId = "WCI_UNNAO_MAIN",
                                        teacherUid = teacherUid,
                                        studentRollNo = activeStudent!!.studentRollNo,
                                        courseId = courseId,
                                        batchId = batchId,
                                        practicalScore = practicalInput.toDoubleOrNull() ?: 0.0,
                                        assignmentScore = assignmentInput.toDoubleOrNull() ?: 0.0,
                                        rubricRemarks = remarksInput
                                    )
                                    OfflineGradeQueue.enqueueGrade(entry)
                                    savedSuccessMsg = "Grade saved for ${activeStudent!!.studentRollNo} (Stored in SQLCipher Offline Queue)"
                                    activeStudent = null
                                    practicalInput = ""
                                    assignmentInput = ""
                                    remarksInput = ""
                                },
                                variant = GlassButtonVariant.PRIMARY,
                                height = 38.dp
                            )
                        }
                    }
                }
            }

            if (savedSuccessMsg != null) {
                Text(
                    text = savedSuccessMsg!!,
                    color = GlassShaderTokens.BrandEmerald,
                    fontSize = 12.sp
                )
            }

            // Student Roster
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(students, key = { it.studentRollNo }) { stu ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stu.studentName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Roll: ${stu.studentRollNo}",
                                    color = Color(0x99FFFFFF),
                                    fontSize = 12.sp
                                )
                            }

                            GlassButton(
                                text = "Grade Practical",
                                onClick = {
                                    activeStudent = stu
                                    savedSuccessMsg = null
                                },
                                variant = GlassButtonVariant.SECONDARY,
                                height = 34.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
