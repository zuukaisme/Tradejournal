package com.example.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.NeonEmerald

data class LiquidGlassNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val selected: Boolean,
    val badgeCount: Int = 0,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun LiquidGlassBottomNavBar(
    items: List<LiquidGlassNavItem>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val glassShape = RoundedCornerShape(28.dp)
    val accentColor = MaterialTheme.colorScheme.primary

    val containerBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xE8131D28),
                Color(0xF0080D14)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF2FFFFFF),
                Color(0xEDF1F5F9)
            )
        )
    }

    val specularRimBrush = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                Color.White.copy(alpha = 0.22f),
                NeonEmerald.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.04f)
            )
        } else {
            listOf(
                Color.White,
                accentColor.copy(alpha = 0.20f),
                Color(0x180F172A)
            )
        }
    )

    val activePillBrush = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                NeonEmerald.copy(alpha = 0.26f),
                NeonEmerald.copy(alpha = 0.11f)
            )
        } else {
            listOf(
                accentColor.copy(alpha = 0.18f),
                accentColor.copy(alpha = 0.09f)
            )
        }
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("bottom_navigation_bar")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    elevation = if (isDarkTheme) 18.dp else 10.dp,
                    shape = glassShape,
                    ambientColor = if (isDarkTheme) NeonEmerald.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                    spotColor = if (isDarkTheme) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.14f)
                )
                .clip(glassShape)
                .background(containerBrush)
                .border(width = 0.75.dp, brush = specularRimBrush, shape = glassShape)
                .padding(5.dp)
        ) {
            val itemWidth = maxWidth / items.size.coerceAtLeast(1)
            val animatedOffsetFraction by animateFloatAsState(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "BottomNavGlassSlide"
            )

            // Sliding liquid-glass capsule behind the active tab
            Box(
                modifier = Modifier
                    .offset(x = itemWidth * animatedOffsetFraction)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(23.dp))
                    .background(activePillBrush)
                    .border(
                        width = 0.75.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isDarkTheme) 0.45f else 0.35f),
                                accentColor.copy(alpha = 0.12f)
                            )
                        ),
                        shape = RoundedCornerShape(23.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val contentColor by animateColorAsState(
                        targetValue = if (item.selected) {
                            accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        },
                        animationSpec = tween(durationMillis = 180),
                        label = "BottomNavColor_${item.label}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(23.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = item.onClick
                            )
                            .testTag(item.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (item.badgeCount > 0) {
                                        Badge(
                                            containerColor = accentColor,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = if (item.badgeCount > 99) "99+" else "${item.badgeCount}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                color = contentColor,
                                fontSize = 11.sp,
                                fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val activeFg = MaterialTheme.colorScheme.primary

    Surface(
        color = trackColor,
        shape = RoundedCornerShape(20.dp),
        border = null,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("liquid_glass_tab_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val icon = icons?.getOrNull(index)

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) activeBg else Color.Transparent,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "TabBgColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) activeFg else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 220),
                    label = "TabTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) }
                        .padding(horizontal = 4.dp)
                        .testTag("liquid_tab_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = title,
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onClick() }
    } else {
        modifier.fillMaxWidth()
    }.let { if (testTag != null) it.testTag(testTag) else it }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (!isDarkTheme) BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            content = content
        )
    }
}

@Composable
fun LiquidGlassPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val bg = if (isSelected) activeColor.copy(alpha = 0.16f) else trackColor
    val contentColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = null,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
