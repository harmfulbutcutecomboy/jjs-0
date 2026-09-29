package com.jjs.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjs.studio.ui.theme.*
import com.jjs.studio.viewmodel.AppScreen

@Composable
fun DotMatrixGrid(
    modifier: Modifier = Modifier,
    dotSpacing: Float = 36f,
    dotRadius: Float = 1.5f,
    dotColor: Color = Color(0xFF20232B)
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        var x = dotSpacing / 2
        while (x < width) {
            var y = dotSpacing / 2
            while (y < height) {
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(x, y)
                )
                y += dotSpacing
            }
            x += dotSpacing
        }
    }
}

@Composable
fun GlowingBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1B1D25), Color(0xFF0C0D11))
                )
            )
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF383C4A), Color(0xFF1E2028))
                    )
                ),
                RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = StarkWhite,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SleekCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    testTag: String = "sleek_card",
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .testTag(testTag)
            .clip(shape)
            .background(CardBackground)
            .border(BorderStroke(1.dp, BorderSubtle), shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(20.dp),
        content = content
    )
}

@Composable
fun FloatingCapsuleHeader(
    currentScreen: AppScreen,
    onScreenSelect: (AppScreen) -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Top Pill Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0D0E13))
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon Badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF7D7F8), Color(0xFFC45EC8), Color(0xFF6B2D72))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ViewInAr,
                    contentDescription = "JJS Logo",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    text = "JJS STUDIO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarkWhite,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Roblox FX & Timeline",
                    fontSize = 10.sp,
                    color = MutedGray
                )
            }

            Spacer(Modifier.weight(1f))

            // Action: Import / Paste
            IconButton(
                onClick = onImportClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161820))
                    .testTag("header_import_button")
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = "Import Code",
                    tint = StarkWhite,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // Action: Export / Save
            IconButton(
                onClick = onExportClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(StarkWhite)
                    .testTag("header_export_button")
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Save / Export",
                    tint = BlackBackground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Navigation Tabs (Horizontal Pill Switcher)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0B0E))
                .border(BorderStroke(1.dp, Color(0xFF181A22)), RoundedCornerShape(16.dp))
                .padding(4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NavTabItem(
                label = "Studio",
                icon = Icons.Default.Widgets,
                selected = currentScreen == AppScreen.HOME,
                onClick = { onScreenSelect(AppScreen.HOME) },
                testTag = "nav_tab_studio"
            )
            NavTabItem(
                label = "Convert",
                icon = Icons.Default.SwapHoriz,
                selected = currentScreen == AppScreen.CONVERT,
                onClick = { onScreenSelect(AppScreen.CONVERT) },
                testTag = "nav_tab_convert"
            )
            NavTabItem(
                label = "Editor",
                icon = Icons.Default.Timeline,
                selected = currentScreen == AppScreen.EDITOR,
                onClick = { onScreenSelect(AppScreen.EDITOR) },
                testTag = "nav_tab_editor"
            )
            NavTabItem(
                label = "Explorer",
                icon = Icons.Default.FolderOpen,
                selected = currentScreen == AppScreen.EXPLORER,
                onClick = { onScreenSelect(AppScreen.EXPLORER) },
                testTag = "nav_tab_explorer"
            )
            NavTabItem(
                label = "Code",
                icon = Icons.Default.Code,
                selected = currentScreen == AppScreen.CODE_EDITOR,
                onClick = { onScreenSelect(AppScreen.CODE_EDITOR) },
                testTag = "nav_tab_code"
            )
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bg = if (selected) Color(0xFF22242D) else Color.Transparent
    val border = if (selected) BorderStroke(1.dp, Color(0xFF383C4A)) else null
    val contentColor = if (selected) StarkWhite else MutedGray

    Row(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
fun FlowArrowDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF14161E))
                    .border(BorderStroke(1.dp, BorderSubtle), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = "Flow downward",
                    tint = MutedGray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun MonospaceCodeCard(
    title: String,
    code: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    maxLines: Int = 5,
    testTag: String = "code_card"
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .testTag(testTag)
            .clip(shape)
            .background(Color(0xFF08090C))
            .border(BorderStroke(1.dp, BorderSubtle), shape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MutedGray,
                    letterSpacing = 0.5.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = SubtleGray
                    )
                }
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161820))
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = StarkWhite,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF040406))
                .border(BorderStroke(1.dp, Color(0xFF161820)), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = if (code.isBlank()) "// No code generated yet" else code,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (code.isBlank()) SubtleGray else Color(0xFFD6C8E8),
                lineHeight = 16.sp,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
