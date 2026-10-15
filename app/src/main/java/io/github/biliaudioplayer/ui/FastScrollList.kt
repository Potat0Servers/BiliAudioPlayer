package io.github.biliaudioplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToInt

/** A draggable right rail targets item indices, so jumps do not require traversing intermediate rows. */
@Composable
internal fun FastScrollList(resetKey: Any? = null, targetIndex: Int? = null, targetRequest: Long? = null, content: LazyListScope.() -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(resetKey, targetIndex, targetRequest) { listState.scrollToItem(targetIndex ?: 0) }
    val requests = remember { Channel<Int>(Channel.CONFLATED) }
    LaunchedEffect(listState) { for (index in requests) listState.scrollToItem(index) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(end = 48.dp, bottom = 24.dp), content = content)
        val info = listState.layoutInfo
        if (info.totalItemsCount > 0 && (listState.canScrollForward || listState.canScrollBackward)) {
            val viewport = with(LocalDensity.current) { maxHeight.toPx() }
            val thumb = with(LocalDensity.current) { 48.dp.toPx() }.coerceAtMost(viewport)
            val travel = (viewport - thumb).coerceAtLeast(1f)
            val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
            val lastIndex = (info.totalItemsCount - visibleCount).coerceAtLeast(1)
            val fraction = if (!listState.canScrollForward) 1f else (listState.firstVisibleItemIndex.toFloat() / lastIndex).coerceIn(0f, 1f)
            fun jump(position: Offset) {
                val proportion = ((position.y - thumb / 2) / travel).coerceIn(0f, 1f)
                requests.trySend((proportion * (info.totalItemsCount - 1)).roundToInt())
            }
            Box(
                Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight()
                    .semantics {
                        contentDescription = "快速滚动条"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        setProgress { value -> requests.trySend((value.coerceIn(0f, 1f) * (info.totalItemsCount - 1)).roundToInt()); true }
                    }
                    .pointerInput(info.totalItemsCount, viewport) { detectTapGestures { jump(it) } }
                    .pointerInput(info.totalItemsCount, viewport) {
                        detectDragGestures(onDragStart = { jump(it) }) { change, _ -> change.consume(); jump(change.position) }
                    },
            ) {
                Box(Modifier.align(Alignment.Center).width(6.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(4.dp)))
                Box(Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (fraction * travel).roundToInt()) }.width(14.dp).height(with(LocalDensity.current) { thumb.toDp() }).background(Coral, RoundedCornerShape(8.dp)))
            }
        }
    }
}
