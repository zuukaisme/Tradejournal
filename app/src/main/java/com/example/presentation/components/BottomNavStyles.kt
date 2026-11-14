package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BottomNavStyle
import com.example.ui.theme.NeonEmerald
import java.util.Locale

@Composable
fun ThemedBottomNavBar(
    items: List<LiquidGlassNavItem>,
    style: BottomNavStyle,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    when (style) {
        BottomNavStyle.LIQUID_GLASS -> LiquidGlassBottomNavBar(
            items = items,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        BottomNavStyle.NEON_ORBIT -> NeonOrbitBottomNavBar(
            items = items,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        BottomNavStyle.ARCADE_RETRO -> ArcadeRetroBottomNavBar(
            items = items,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        BottomNavStyle.MINIMAL_RAIL -> MinimalUnderbarBottomNavBar(
            items = items,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        BottomNavStyle.BUBBLE_BOUNCE -> BubbleBounceBottomNavBar(
            items = items,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
    }
}

@Composable
private fun NavBadgeIcon(
    item: LiquidGlassNavItem,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp
) {
    val accent = MaterialTheme.colorScheme.primary
    BadgedBox(
        badge = {
            if (item.badgeCount > 0) {
                Badge(
                    containerColor = accent,
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
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

// Style 2: Neon Orbit — elevated circular badge pops above bar with glowing dot
@Composable
private fun NeonOrbitBottomNavBar(
    items: List<LiquidGlassNavItem>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val containerColor = if (isDarkTheme) Color(0xFF0C141E) else MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 12.dp)
            .testTag("bottom_navigation_bar")
    ) {
        Surface(
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = if (isDarkTheme) 12.dp else 6.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {}

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val popOffset by animateDpAsState(
                    targetValue = if (item.selected) (-12).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "OrbitPop_${item.label}"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (item.selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "OrbitColor_${item.label}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .minimumInteractiveComponentSize()
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
                        Box(
                            modifier = Modifier
                                .offset(y = popOffset)
                                .size(40.dp)
                                .then(
                                    if (item.selected) {
                                        Modifier
                                            .shadow(
                                                elevation = 10.dp,
                                                shape = CircleShape,
                                                ambientColor = NeonEmerald.copy(alpha = 0.45f),
                                                spotColor = NeonEmerald.copy(alpha = 0.45f)
                                            )
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        accent,
                                                        accent.copy(alpha = 0.85f)
                                                    )
                                                )
                                            )
                                            .border(
                                                width = 2.dp,
                                                color = containerColor,
                                                shape = CircleShape
                                            )
                                    } else {
                                        Modifier.clip(CircleShape)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            NavBadgeIcon(
                                item = item,
                                tint = if (item.selected) MaterialTheme.colorScheme.onPrimary else contentColor,
                                iconSize = 20.dp
                            )
                        }

                        Text(
                            text = item.label,
                            color = contentColor,
                            fontSize = 10.sp,
                            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )

                        if (item.selected) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 14.dp, height = 3.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Style 3: 8-Bit Arcade — boxy retro terminal blocks with monospace uppercase labels
@Composable
private fun ArcadeRetroBottomNavBar(
    items: List<LiquidGlassNavItem>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = if (isDarkTheme) Color(0xFF070C12) else Color(0xFFF3F7F4)
    val borderColor = if (isDarkTheme) accent.copy(alpha = 0.55f) else accent.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("bottom_navigation_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .border(2.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val blockBg by animateColorAsState(
                    targetValue = if (item.selected) accent else Color.Transparent,
                    animationSpec = tween(120),
                    label = "ArcadeBg_${item.label}"
                )
                val fgColor by animateColorAsState(
                    targetValue = if (item.selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(120),
                    label = "ArcadeFg_${item.label}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(2.dp))
                        .background(blockBg)
                        .then(
                            if (!item.selected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = borderColor.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                            } else Modifier
                        )
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
                        NavBadgeIcon(
                            item = item,
                            tint = fgColor,
                            iconSize = 18.dp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label.uppercase(Locale.US),
                            color = fgColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Style 4: Minimal Underbar — sleek full-width dock with gliding top underline bar
@Composable
private fun MinimalUnderbarBottomNavBar(
    items: List<LiquidGlassNavItem>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val accent = MaterialTheme.colorScheme.primary
    val surfaceColor = if (isDarkTheme) Color(0xFF0A1018) else MaterialTheme.colorScheme.surface

    Surface(
        color = surfaceColor,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("bottom_navigation_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val itemWidth = maxWidth / items.size.coerceAtLeast(1)
                val indicatorOffsetFraction by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "MinimalRailSlide"
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        val scale by animateFloatAsState(
                            targetValue = if (item.selected) 1.06f else 1.0f,
                            animationSpec = tween(160),
                            label = "MinimalScale_${item.label}"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (item.selected) {
                                accent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            animationSpec = tween(160),
                            label = "MinimalColor_${item.label}"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .minimumInteractiveComponentSize()
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
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                            ) {
                                NavBadgeIcon(
                                    item = item,
                                    tint = contentColor,
                                    iconSize = 22.dp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Sliding 3dp top indicator bar
                Box(
                    modifier = Modifier
                        .offset(x = itemWidth * indicatorOffsetFraction)
                        .width(itemWidth)
                        .height(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(accent)
                    )
                }
            }
        }
    }
}

// Style 5: Bouncy Pill — playful horizontal pill expands on active tab with spring overshoot
@Composable
private fun BubbleBounceBottomNavBar(
    items: List<LiquidGlassNavItem>,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val dockColor = if (isDarkTheme) Color(0xFF101924) else MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("bottom_navigation_bar")
    ) {
        Surface(
            color = dockColor,
            shape = CircleShape,
            shadowElevation = if (isDarkTheme) 14.dp else 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val pillBg by animateColorAsState(
                        targetValue = if (item.selected) {
                            accent.copy(alpha = if (isDarkTheme) 0.22f else 0.16f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(200),
                        label = "BubbleBg_${item.label}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (item.selected) 1.12f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "BubbleScale_${item.label}"
                    )
                    val fgColor by animateColorAsState(
                        targetValue = if (item.selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "BubbleFg_${item.label}"
                    )

                    Box(
                        modifier = Modifier
                            .then(
                                if (item.selected) Modifier.weight(1.65f) else Modifier.weight(1f)
                            )
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(pillBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = item.onClick
                            )
                            .padding(horizontal = 10.dp)
                            .testTag(item.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)) {
                                NavBadgeIcon(
                                    item = item,
                                    tint = fgColor,
                                    iconSize = 21.dp
                                )
                            }
                            AnimatedVisibility(
                                visible = item.selected,
                                enter = fadeIn(tween(150)) + expandHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ),
                                exit = fadeOut(tween(100)) + shrinkHorizontally()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.label,
                                        color = fgColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
