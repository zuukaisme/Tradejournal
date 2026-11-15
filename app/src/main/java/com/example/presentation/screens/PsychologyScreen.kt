package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Emotion
import com.example.domain.model.Trade
import com.example.domain.model.TradeResult
import com.example.presentation.TradingViewModel
import java.util.Locale

private data class EmotionPerformance(
    val emotion: Emotion,
    val trades: Int,
    val wins: Int,
    val netPnL: Double
) {
    val winRate: Float
        get() = if (trades == 0) 0f else wins.toFloat() / trades
}

@Composable
fun PsychologyScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val trades by viewModel.trades.collectAsState()
    val reviews by viewModel.dailyReviews.collectAsState()

    val validTrades = remember(trades) {
        trades.filter { it.result != TradeResult.NO_TRADE }
    }
    val emotionPerformance = remember(validTrades) {
        Emotion.values().mapNotNull { emotion ->
            val matching = validTrades.filter { it.emotion == emotion }
            if (matching.isEmpty()) null else EmotionPerformance(
                emotion = emotion,
                trades = matching.size,
                wins = matching.count { it.pnl > 0.0 || it.result == TradeResult.WIN },
                netPnL = matching.sumOf { it.pnl }
            )
        }.sortedByDescending { it.trades }
    }
    val ruleFollowed = validTrades.count { it.followedRules }
    val ruleRate = if (validTrades.isEmpty()) 0f else ruleFollowed.toFloat() / validTrades.size
    val averageDiscipline = if (reviews.isEmpty()) 0.0 else reviews.take(30).map { it.disciplineRating }.average()
    val revengeTrades = validTrades.count { it.emotion == Emotion.REVENGE }
    val fearTrades = validTrades.count { it.emotion == Emotion.FEARFUL }
    val greedTrades = validTrades.count { it.emotion == Emotion.GREEDY }
    val ruleBreaks = validTrades.count { !it.followedRules }
    val recentReviews = reviews.sortedByDescending { it.date }.take(10)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("Trading Psychology", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Understand how your mindset affects execution", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = { viewModel.openDailyReview() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                        Text("Complete today's review", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Rule adherence",
                    value = if (validTrades.isEmpty()) "—" else "${(ruleRate * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Avg. discipline",
                    value = if (reviews.isEmpty()) "—" else String.format(Locale.US, "%.1f/5", averageDiscipline),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Emotional risk patterns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Based on the emotions recorded on your trades.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RiskPatternRow("Revenge trading", revengeTrades)
                    RiskPatternRow("Fear-driven trades", fearTrades)
                    RiskPatternRow("Greed-driven trades", greedTrades)
                    RiskPatternRow("Trades that broke your rules", ruleBreaks)
                }
            }
        }

        item {
            Text("Performance by emotion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (emotionPerformance.isEmpty()) {
            item {
                EmptyPsychologyCard("No trade emotions recorded yet. Record an emotion when adding or editing a trade to build this analysis.")
            }
        } else {
            items(emotionPerformance) { performance ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(performance.emotion.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                String.format(Locale.US, "%.1f%% win rate", performance.winRate * 100),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        LinearProgressIndicator(
                            progress = { performance.winRate },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${performance.trades} trades · ${performance.wins} wins", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                String.format(Locale.US, "%+.2f", performance.netPnL),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (performance.netPnL >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Recent daily reflections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (recentReviews.isEmpty()) {
            item {
                EmptyPsychologyCard("Your saved daily reviews will appear here.")
            }
        } else {
            items(recentReviews) { review ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(review.date, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${review.disciplineRating}/5 · ${if (review.followedAllRules) "Rules followed" else "Rules broken"}",
                                color = if (review.followedAllRules) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (review.reflection.isNotBlank()) Text(review.reflection, style = MaterialTheme.typography.bodySmall)
                        if (review.lessonsLearned.isNotBlank()) {
                            Text("Next time: ${review.lessonsLearned}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RiskPatternRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyPsychologyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
