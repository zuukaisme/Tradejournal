package com.example.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.backtest.AiInstructionStore

@Composable
fun AiInstructionsSettingsCard() {
    val context = LocalContext.current
    var instructions by remember { mutableStateOf(AiInstructionStore.load()) }
    var savedInstructions by remember { mutableStateOf(instructions) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("AI Instructions", style = MaterialTheme.typography.titleMedium)
            Text(
                "Set additional instructions used by AI features across TradePulse, including strategy generation and AI analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it.take(12000) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 12,
                label = { Text("Your custom AI instructions") },
                placeholder = {
                    Text("Example: Explain trading concepts in simple language. Prefer price action and SMC terminology.")
                },
                supportingText = {
                    Text(instructions.length.toString() + "/12000 characters")
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        runCatching {
                            AiInstructionStore.save(instructions)
                            savedInstructions = instructions.trim()
                            Toast.makeText(context, "AI instructions saved", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, it.message ?: "Unable to save instructions", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = instructions.trim() != savedInstructions,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save instructions")
                }
                TextButton(
                    onClick = {
                        instructions = ""
                        AiInstructionStore.clear()
                        savedInstructions = ""
                        Toast.makeText(context, "Custom AI instructions cleared", Toast.LENGTH_SHORT).show()
                    },
                    enabled = instructions.isNotBlank() || savedInstructions.isNotBlank()
                ) {
                    Text("Reset")
                }
            }
            Text(
                "These instructions are added to AI system prompts. They do not replace the built-in requirements for generated strategies.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
