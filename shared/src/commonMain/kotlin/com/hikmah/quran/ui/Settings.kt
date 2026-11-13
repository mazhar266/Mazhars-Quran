package com.hikmah.quran.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hikmah.quran.features.Feature
import com.hikmah.quran.features.FeatureController
import com.hikmah.quran.features.FeatureStatus
import com.hikmah.quran.features.isOn
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    features: FeatureController,
    statuses: Map<Feature, FeatureStatus>,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Surahs") }
            Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
        HorizontalDivider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            for (feature in Feature.entries) {
                FeatureRow(feature, statuses[feature] ?: FeatureStatus.Off(downloaded = false), features)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text(
                "Both are optional. Without them this is a plain reader: nothing is downloaded and the " +
                    "microphone is never used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun FeatureRow(feature: Feature, status: FeatureStatus, features: FeatureController) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(feature.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(checked = status.isOn, onCheckedChange = { features.setEnabled(feature, it) })
        }

        val size = features.downloadSize(feature)
        val line = when (status) {
            is FeatureStatus.Off ->
                if (status.downloaded) "Off. The $size download is still on this device."
                else "Off. Turning it on downloads $size."
            is FeatureStatus.Downloading -> buildString {
                append("Downloading $size")
                status.progress?.let { append(" · ${(it * 100).roundToInt()}%") }
                status.note?.let { append(" · $it") }
            }
            FeatureStatus.Preparing -> "Checking the download and getting ready…"
            FeatureStatus.Ready -> "On and ready."
            is FeatureStatus.Failed -> "Couldn't turn on: ${status.reason}"
        }
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = if (status is FeatureStatus.Failed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        when (status) {
            is FeatureStatus.Downloading -> {
                val progress = status.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            FeatureStatus.Preparing -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            is FeatureStatus.Failed -> TextButton(onClick = { features.setEnabled(feature, true) }) { Text("Try again") }
            is FeatureStatus.Off -> if (status.downloaded) {
                TextButton(onClick = { features.deleteDownload(feature) }) { Text("Delete download") }
            }
            FeatureStatus.Ready -> Unit
        }
    }
}
