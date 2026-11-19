package com.example.presentation.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.presentation.components.LiquidGlassCard
import java.util.Locale

@Composable
fun Mt5RuntimeDiagnosticsCard() {
    val context = LocalContext.current
    val diagnostics = remember(context) { collectRuntimeDiagnostics(context) }

    LiquidGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Embedded MT5 Runtime",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Device compatibility check",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        DiagnosticLine("Device", diagnostics.device)
        DiagnosticLine("Android", diagnostics.android)
        DiagnosticLine("CPU ABI", diagnostics.abis)
        DiagnosticLine("CPU cores", diagnostics.cpuCores)
        DiagnosticLine("RAM", diagnostics.ram)
        DiagnosticLine("Free app storage", diagnostics.storage)
        DiagnosticLine("Graphics features", diagnostics.graphics)

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Runtime status: Not installed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = diagnostics.assessment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "These checks only report device capabilities. They do not confirm that Wine, Box64 or MetaTrader 5 can run on this phone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.85f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.15f)
        )
    }
}

private data class RuntimeDiagnostics(
    val device: String,
    val android: String,
    val abis: String,
    val cpuCores: String,
    val ram: String,
    val storage: String,
    val graphics: String,
    val assessment: String
)

private fun collectRuntimeDiagnostics(context: Context): RuntimeDiagnostics {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val statFs = StatFs(context.filesDir.absolutePath)
    val freeBytes = statFs.availableBytes
    val totalRamGb = memoryInfo.totalMem / 1024.0 / 1024.0 / 1024.0
    val availableRamGb = memoryInfo.availMem / 1024.0 / 1024.0 / 1024.0
    val freeStorageGb = freeBytes / 1024.0 / 1024.0 / 1024.0
    val abis = Build.SUPPORTED_ABIS.joinToString()
    val features = context.packageManager.systemAvailableFeatures
        .mapNotNull { it.name }
        .filter { it.contains("vulkan", ignoreCase = true) }
        .sorted()
        .joinToString()
        .ifBlank { "No Vulkan feature reported" }

    val graphics = when {
        features.contains("vulkan", ignoreCase = true) -> features
        else -> features
    }

    val chipsetHint = when {
        Build.MODEL.contains("SM-A125", ignoreCase = true) -> "Helio P35 variant"
        Build.MODEL.contains("SM-A127", ignoreCase = true) -> "Exynos 850 variant"
        else -> "Chipset not identified from model name"
    }

    val assessment = when {
        availableRamGb < 1.0 -> "Available RAM is currently below 1 GB. A Wine/Box64 desktop runtime is unlikely to be practical while the phone is under this memory pressure."
        freeStorageGb < 3.0 -> "Less than 3 GB is free in app storage. An extracted Windows compatibility environment would need more room."
        else -> "$chipsetHint. This A12 generation uses entry-level hardware; Wine/Box64 and MT5 compatibility is uncertain, especially with Mali graphics. We will verify the exact variant before attempting runtime installation."
    }

    return RuntimeDiagnostics(
        device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        abis = abis.ifBlank { "Unavailable" },
        cpuCores = Runtime.getRuntime().availableProcessors().toString(),
        ram = String.format(Locale.US, "%.1f GB total / %.1f GB available", totalRamGb, availableRamGb),
        storage = String.format(Locale.US, "%.1f GB", freeStorageGb),
        graphics = graphics,
        assessment = assessment
    )
}
