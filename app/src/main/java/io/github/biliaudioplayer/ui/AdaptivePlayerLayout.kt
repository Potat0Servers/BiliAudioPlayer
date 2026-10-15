package io.github.biliaudioplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Six slots: queue title, cover, title/artist, seek controls, playback controls, status. */
@Composable
internal fun AdaptivePlayerLayout(viewportHeight: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { children, constraints ->
        val width = constraints.maxWidth
        val measured = arrayOfNulls<androidx.compose.ui.layout.Placeable>(6)
        val contentConstraints = Constraints(minWidth = width, maxWidth = width)
        for (index in children.indices) if (index != 1) measured[index] = children[index].measure(contentConstraints)
        val fixedHeight = measured.filterNotNull().sumOf { it.height }
        val normal = listOf(18, 22, 32, 22, 18, 18, 28).map { it.dp.roundToPx() }
        val minimum = listOf(6, 6, 8, 6, 6, 6, 8).map { it.dp.roundToPx() }
        val preferredCover = minOf(width, 390.dp.roundToPx())
        val compressible = (normal.sum() - minimum.sum()).coerceAtLeast(1)
        val shortage = (fixedHeight + preferredCover + normal.sum() - viewportHeight).coerceAtLeast(0)
        val compression = (shortage.toFloat() / compressible).coerceIn(0f, 1f)
        val gaps = normal.indices.map { index -> (normal[index] - (normal[index] - minimum[index]) * compression).roundToInt() }
        val coverSize = minOf(preferredCover, (viewportHeight - fixedHeight - gaps.sum()).coerceAtLeast(0))
        measured[1] = children[1].measure(Constraints.fixed(coverSize, coverSize))
        // Exceptionally short windows / accessibility fonts can still scroll once the cover reaches zero.
        val height = maxOf(viewportHeight, fixedHeight + coverSize + gaps.sum())
        layout(width, height) {
            var y = gaps[0]
            measured.forEachIndexed { index, placeable ->
                placeable?.placeRelative((width - placeable.width) / 2, y)
                y += (placeable?.height ?: 0) + gaps[index + 1]
            }
        }
    }
}
