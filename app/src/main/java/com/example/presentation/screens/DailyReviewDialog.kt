package com.example.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DailyReview
import com.example.presentation.TradingViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewBottomSheet(
    viewModel: TradingViewModel,
    reviewDate: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val effectiveDate = reviewDate ?: sdf.format(Calendar.getInstance().time)

    var existingReview by remember { mutableStateOf<DailyReview?>(null) }
    var rating by remember { mutableIntStateOf(5) }
    var reflection by remember { mutableStateOf("") }
    var lessons by remember { mutableStateOf("") }
    var followedRules by remember { mutableStateOf(true) }

    LaunchedEffect(effectiveDate) {
        val rev = viewModel.repository.getReviewByDate(effectiveDate)
        existingReview = rev
        if (rev != null) {
            rating = rev.disciplineRating
            reflection = rev.reflection
            lessons = rev.lessonsLearned
            followedRules = rev.followedAllRules
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Trading Psychology Review",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Review for $effectiveDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1 to 5 Discipline Star Rating
            Text(
                text = "Trading Discipline Rating",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (star in 1..5) {
                    Icon(
                        imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "Star $star",
                        tint = if (star <= rating) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { rating = star }
                            .testTag("star_$star")
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (rating) {
                        5 -> "Flawless Execution"
                        4 -> "Good Discipline"
                        3 -> "Minor Deviations"
                        2 -> "Emotional Trading"
                        else -> "Revenge / FOMO"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Followed Rules Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Did you follow your strategy rules 100%?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = followedRules,
                    onCheckedChange = { followedRules = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = reflection,
                onValueChange = { reflection = it },
                label = { Text("How did you feel and what went well?") },
                placeholder = { Text("Confident, anxious, impatient, focused, patient...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = lessons,
                onValueChange = { lessons = it },
                label = { Text("Mistakes, triggers & lessons learned") },
                placeholder = { Text("FOMO, revenge trading, overtrading, early exits, missed confirmations...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.saveDailyReview(
                        DailyReview(
                            date = effectiveDate,
                            disciplineRating = rating,
                            reflection = reflection.trim(),
                            lessonsLearned = lessons.trim(),
                            followedAllRules = followedRules,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_daily_review_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Daily Review")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
