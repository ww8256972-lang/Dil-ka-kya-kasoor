package com.wci.ui.liquidglass.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.components.GlassPill
import com.wci.ui.liquidglass.components.GlassSurface
import com.wci.ui.liquidglass.shaders.liquidGlassEffect
import com.wci.ui.liquidglass.theme.GlassShaderTokens

data class SidebarNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * CurvedLiquidSidebar (Section 2 & PRD Navigation Matrix)
 * Sculpted 3D Ultra Liquid Glass concave sidebar with AGSL refraction,
 * active item glow, single-campus lock badge, and 10-rule Terms anchor.
 */
@Composable
fun CurvedLiquidSidebar(
    items: List<SidebarNavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onOpenTerms: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sidebarShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 24.dp,
        bottomEnd = 24.dp
    )

    GlassSurface(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .liquidGlassEffect(refractionIndex = 1.05f),
        shape = sidebarShape,
        backgroundColor = GlassShaderTokens.BrandMidnight.copy(alpha = 0.85f),
        borderBrush = GlassShaderTokens.SpecularBorderGradient,
        borderWidth = 1.dp,
        elevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 20.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Institute Header & Campus Badge
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassShaderTokens.ActiveGlowGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "WCI",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Waqar Institute",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Main Campus",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                GlassPill(
                    text = "UNNAO MAIN (26.538°N, 80.484°E)",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = GlassShaderTokens.BrandCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Navigation Items List
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f, fill = false)
                ) {
                    items.forEach { item ->
                        val isSelected = currentRoute == item.route
                        val itemBg by animateColorAsState(
                            if (isSelected) GlassShaderTokens.GlassTintProminent else Color.Transparent,
                            label = "nav_item_bg"
                        )
                        val itemTextColor by animateColorAsState(
                            if (isSelected) Color.White else Color(0xB3FFFFFF),
                            label = "nav_item_text"
                        )
                        val iconColor by animateColorAsState(
                            if (isSelected) GlassShaderTokens.BrandCyan else Color(0x80FFFFFF),
                            label = "nav_item_icon"
                        )

                        val itemShape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(itemShape)
                                .background(itemBg)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.dp, GlassShaderTokens.BrandCyan.copy(alpha = 0.5f), itemShape)
                                    } else Modifier
                                )
                                .clickable { onNavigate(item.route) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.title,
                                    color = itemTextColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.badgeCount > 0) {
                                    GlassPill(
                                        text = item.badgeCount.toString(),
                                        color = GlassShaderTokens.BrandCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Actions: 10-Rule Terms & Graceful Logout (F-020)
            Column(modifier = Modifier.padding(top = 12.dp)) {
                // 10-Rule Terms Anchor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenTerms() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Terms",
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "10-Rule Campus Terms",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // F-020 Cryptographic Logout Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLogout() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Logout",
                        tint = GlassShaderTokens.BrandRuby,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Secure Logout & Zeroize",
                        color = GlassShaderTokens.BrandRuby,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
