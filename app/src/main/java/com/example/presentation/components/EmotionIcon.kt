package com.example.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Emotion
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SlateBreakEven

fun getEmotionIcon(emotion: Emotion): ImageVector = when (emotion) {
    Emotion.CALM -> Icons.Default.SelfImprovement
    Emotion.CONFIDENT -> Icons.Default.Shield
    Emotion.FEARFUL -> Icons.Default.Warning
    Emotion.GREEDY -> Icons.Default.TrendingUp
    Emotion.REVENGE -> Icons.Default.FlashOn
    Emotion.NEUTRAL -> Icons.Default.Balance
}

fun getEmotionColor(emotion: Emotion): Color = when (emotion) {
    Emotion.CALM -> EmeraldGreen
    Emotion.CONFIDENT -> Color(0xFF3B82F6) // Electric Blue
    Emotion.FEARFUL -> Color(0xFFF59E0B) // Amber
    Emotion.GREEDY -> Color(0xFF8B5CF6) // Purple
    Emotion.REVENGE -> CrimsonRed
    Emotion.NEUTRAL -> SlateBreakEven
}

@Composable
fun EmotionBadge(
    emotion: Emotion,
    modifier: Modifier = Modifier
) {
    val color = getEmotionColor(emotion)
    val icon = getEmotionIcon(emotion)

    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = emotion.label,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = emotion.label,
                color = color,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
        }
    }
}
