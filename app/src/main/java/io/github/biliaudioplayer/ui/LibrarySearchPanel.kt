package io.github.biliaudioplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.biliaudioplayer.domain.SearchHit
import io.github.biliaudioplayer.domain.SearchKind

@Composable
fun LibrarySearchPanel(
    query: String,
    kinds: Set<SearchKind>,
    hits: List<SearchHit>,
    loading: Boolean,
    progress: String,
    onQueryChange: (String) -> Unit,
    onKindsChange: (Set<SearchKind>) -> Unit,
    onHitClick: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索全部收藏") },
            placeholder = { Text("收藏夹、稿件、分P、UP主或BV号") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                }
            },
            shape = RoundedCornerShape(18.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind in kinds,
                    onClick = { onKindsChange(if (kind in kinds) kinds - kind else kinds + kind) },
                    label = { Text(when (kind) {
                        SearchKind.FOLDER -> "收藏夹"
                        SearchKind.VIDEO -> "稿件"
                        SearchKind.PART -> "分P"
                    }) },
                )
            }
        }
        if (query.isNotBlank()) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(progress.ifBlank { "正在检索全部收藏…" }, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(
                when {
                    kinds.isEmpty() -> "请选择至少一种搜索类型"
                    hits.isNotEmpty() -> "找到 ${hits.size} 条结果"
                    loading -> "索引建立后会逐步显示结果"
                    else -> "没有找到匹配结果"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            if (hits.isNotEmpty()) Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(hits, key = { it.key }) { hit ->
                        Column(Modifier.fillMaxWidth().clickable { onHitClick(hit) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(hit.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(hit.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
