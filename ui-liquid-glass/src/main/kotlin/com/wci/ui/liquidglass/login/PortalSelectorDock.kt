package com.wci.ui.liquidglass.login

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.theme.GlassShaderTokens

enum class InstitutePortal(val role: String, val title: String) {
    ADMIN("ADMIN", "Admin"),
    TEACHER("TEACHER", "Teacher"),
    STUDENT("STUDENT", "Student"),
    PARENT("PARENT", "Parent")
}

/**
 * PortalSelectorDock (F-001)
 * 3D Liquid Glass Top Center Dock switching active authentication context
 * across Admin, Teacher, Student, and Parent portals with isolated namespaces.
 */
@Composable
fun PortalSelectorDock(
    selectedPortal: InstitutePortal,
    onPortalSelected: (InstitutePortal) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(GlassShaderTokens.CornerRadiusPill)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(dockShape)
            .background(GlassShaderTokens.GlassTintDeep)
            .border(1.dp, GlassShaderTokens.SpecularBorderGradient, dockShape)
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InstitutePortal.values().forEach { portal ->
                val isSelected = selectedPortal == portal
                val pillBg by animateColorAsState(
                    if (isSelected) GlassShaderTokens.BrandAccentBlue else Color.Transparent,
                    label = "portal_pill_bg"
                )
                val textColor by animateColorAsState(
                    if (isSelected) Color.White else Color(0x99FFFFFF),
                    label = "portal_pill_text"
                )
                val pillShape = RoundedCornerShape(GlassShaderTokens.CornerRadiusPill)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(pillShape)
                        .background(pillBg)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, GlassShaderTokens.BrandCyan.copy(alpha = 0.6f), pillShape)
                            } else Modifier
                        )
                        .clickable { onPortalSelected(portal) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = portal.title,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
