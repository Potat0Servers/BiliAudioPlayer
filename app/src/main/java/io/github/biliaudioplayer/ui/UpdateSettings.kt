package io.github.biliaudioplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.biliaudioplayer.update.UpdatePhase
import io.github.biliaudioplayer.update.UpdateUiState

@Composable
fun UpdateSettings(state: UpdateUiState, check: () -> Unit, download: () -> Unit, install: () -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("设置", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("BiliAudioPlayer", style = MaterialTheme.typography.titleLarge)
            Text("当前版本 ${state.currentVersion}", color = Muted)
            state.release?.let { Text("新版本 ${it.versionName}") }
            val phase = state.phase
            Button(onClick = when {
                phase == UpdatePhase.READY -> install
                state.release != null && phase != UpdatePhase.CHECKING -> download
                else -> check
            }, enabled = phase != UpdatePhase.CHECKING && phase != UpdatePhase.DOWNLOADING) {
                Text(when {
                    phase == UpdatePhase.CHECKING -> "正在检查更新…"
                    phase == UpdatePhase.DOWNLOADING -> "正在下载…"
                    phase == UpdatePhase.READY -> "安装更新"
                    state.release != null -> if (phase == UpdatePhase.ERROR) "重新下载" else "下载更新"
                    phase == UpdatePhase.ERROR -> "重试检查更新"
                    else -> "检查更新"
                })
            }
            if (phase == UpdatePhase.DOWNLOADING) {
                val progress = state.progress
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", color = Muted)
                }
            }
            state.message?.let { Text(it, color = if (phase == UpdatePhase.ERROR) MaterialTheme.colorScheme.error else Muted) }
        }
    }
}
