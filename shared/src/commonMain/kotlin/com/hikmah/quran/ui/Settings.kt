package com.hikmah.quran.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hikmah.quran.data.SurahCatalog
import com.hikmah.quran.features.Feature
import com.hikmah.quran.features.FeatureController
import com.hikmah.quran.features.FeatureStatus
import com.hikmah.quran.features.isOn
import com.hikmah.quran.layout.TextSize
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    features: FeatureController?,
    statuses: Map<Feature, FeatureStatus>,
    textSize: TextSize,
    onTextSize: (TextSize) -> Unit,
    onBack: () -> Unit,
) {
    // Set while asking whether a large download may use mobile data.
    var asking by remember { mutableStateOf<Feature?>(null) }
    fun toggle(feature: Feature, on: Boolean) {
        if (features == null) return
        if (on && features.wouldUseMobileData(feature)) asking = feature
        else features.setEnabled(feature, on)
    }
    if (features != null) asking?.let { feature ->
        MobileDataDialog(
            feature = feature,
            size = features.downloadSize(feature),
            onUseMobileData = { features.setEnabled(feature, true, allowMobileData = true); asking = null },
            onWaitForWifi = { features.setEnabled(feature, true, allowMobileData = false); asking = null },
            onCancel = { asking = null },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Surahs") }
            Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
        HorizontalDivider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TextSizeRow(textSize, onTextSize)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (features != null) {
                for (feature in Feature.entries) {
                    FeatureRow(feature, statuses[feature] ?: FeatureStatus.Off(downloaded = false), features, ::toggle)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (features != null) Text(
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
private fun TextSizeRow(size: TextSize, onChange: (TextSize) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Text size", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Size ${size.level} of ${TextSize.entries.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TextSizeButtons(size, onChange)
        }
        // Preview at the size the surahs are read in, using a surah name rather than Quran text.
        Text(
            text = SurahCatalog.all.first().arabicName,
            fontSize = size.readerSp(),
            fontFamily = quranFont(),
            lineHeight = size.readerSp() * 1.6f,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp),
        )
    }
}

@Composable
private fun MobileDataDialog(
    feature: Feature,
    size: String,
    onUseMobileData: () -> Unit,
    onWaitForWifi: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Download $size on mobile data?") },
        text = {
            Text(
                "${feature.title} needs a $size download and you're not on Wi-Fi. " +
                    "Using mobile data may cost money or use up your allowance.",
            )
        },
        // Three choices, stacked so none of them wraps or is orphaned on its own row.
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onWaitForWifi) { Text("Wait for Wi-Fi") }
                TextButton(onClick = onUseMobileData) { Text("Use mobile data") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun FeatureRow(
    feature: Feature,
    status: FeatureStatus,
    features: FeatureController,
    onToggle: (Feature, Boolean) -> Unit,
) {
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
            Switch(checked = status.isOn, onCheckedChange = { onToggle(feature, it) })
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
                if (status.waitingForWifi) {
                    TextButton(onClick = { features.setEnabled(feature, true, allowMobileData = true) }) {
                        Text("Use mobile data instead")
                    }
                }
            }
            FeatureStatus.Preparing -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            is FeatureStatus.Failed -> TextButton(onClick = { onToggle(feature, true) }) { Text("Try again") }
            is FeatureStatus.Off -> if (status.downloaded) {
                TextButton(onClick = { features.deleteDownload(feature) }) { Text("Delete download") }
            }
            FeatureStatus.Ready -> Unit
        }
    }
}
