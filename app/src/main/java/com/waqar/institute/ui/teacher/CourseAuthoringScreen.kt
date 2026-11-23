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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.waqar.institute.data.teacher.CourseDraft
import com.waqar.institute.data.teacher.DraftContentVault
import java.util.UUID

/**
 * CourseAuthoringScreen (M2.6: F-267 to F-282)
 * Course Content & Coding Lab Authoring:
 * - Markdown study notes composer
 * - Coding exercise challenge editor
 * - Continuous background auto-save to DraftContentVault
 */
@Composable
fun CourseAuthoringScreen(
    teacherUid: String,
    courseId: String = "ADCA",
    onPublishComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var chapterTitle by remember { mutableStateOf("") }
    var markdownContent by remember { mutableStateOf("") }
    var codingExercisePrompt by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var autoSaveStatus by remember { mutableStateOf("Auto-saved to Local Vault") }

    // Auto-save on content modification
    LaunchedEffect(chapterTitle, markdownContent, codingExercisePrompt, videoUrl) {
        if (chapterTitle.isNotBlank() || markdownContent.isNotBlank()) {
            val draft = CourseDraft(
                draftId = draftId,
                campusId = "WCI_UNNAO_MAIN",
                authorTeacherUid = teacherUid,
                courseId = courseId,
                title = chapterTitle,
                markdownContent = markdownContent,
                videoResourceUrl = videoUrl,
                codingExercisePrompt = codingExercisePrompt
            )
            DraftContentVault.saveDraft(draft)
            autoSaveStatus = "Auto-saved at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassShaderTokens.BrandNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
                            text = "Course Content & Lab Authoring",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Curriculum: $courseId • Faculty: $teacherUid",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 12.sp
                        )
                    }

                    GlassPill(
                        text = autoSaveStatus,
                        color = GlassShaderTokens.BrandEmerald
                    )
                }
            }

            // Chapter Title & Video Link
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 18.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlassTextField(
                        value = chapterTitle,
                        onValueChange = { chapterTitle = it },
                        label = "Chapter / Module Title",
                        placeholder = "e.g. Chapter 4: Advanced Excel Financial Modeling"
                    )

                    GlassTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = "Reference Lab Video URL (Optional)",
                        placeholder = "https://cdn.waqarinstitute.in/lectures/adca_mod4.mp4"
                    )
                }
            }

            // Markdown Notes Composer
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 18.dp
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Notes",
                            tint = GlassShaderTokens.BrandCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Study Material & Notes (Markdown)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassTextField(
                        value = markdownContent,
                        onValueChange = { markdownContent = it },
                        placeholder = "# Introduction\nWrite detailed theoretical and practical concepts here...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }

            // Coding Challenge Editor
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 18.dp
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Code",
                            tint = GlassShaderTokens.BrandPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Interactive Coding Exercise (Chaquopy / QuickJS)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassTextField(
                        value = codingExercisePrompt,
                        onValueChange = { codingExercisePrompt = it },
                        placeholder = "def calculate_emi(principal, rate, tenure):\n    # Write Python sandbox test challenge",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }

            // Publish Action
            GlassButton(
                text = "Publish to Student LMS",
                onClick = {
                    DraftContentVault.deleteDraft(draftId)
                    onPublishComplete()
                },
                enabled = chapterTitle.isNotBlank() && markdownContent.isNotBlank(),
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
