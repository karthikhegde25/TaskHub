// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kARThik.tasks.data.repository.ItemKind
import com.kARThik.tasks.data.repository.TimelineItem
import com.kARThik.tasks.ui.theme.PriorityColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val HOUR_HEIGHT = 64.dp
private val GUTTER = 52.dp
private const val SNAP_MIN = 15

private class Slot(val item: TimelineItem, val start: Int, val end: Int, var col: Int = 0)
private class Block(val item: TimelineItem, val start: Int, val end: Int, val col: Int, val cols: Int)

/** Splits items into overlap clusters and assigns side-by-side columns. */
private fun layoutBlocks(items: List<TimelineItem>): List<Block> {
    val zone = ZoneId.systemDefault()
    val slots = items.filter { it.hasTime }.map {
        val s = Instant.ofEpochMilli(it.startMillis!!).atZone(zone).toLocalTime()
        val start = s.hour * 60 + s.minute
        val dur = if (it.endMillis != null && it.endMillis > it.startMillis) ((it.endMillis - it.startMillis) / 60_000L).toInt() else 60
        Slot(it, start, min(24 * 60, start + max(dur, SNAP_MIN)))
    }.sortedBy { it.start }

    val out = ArrayList<Block>()
    var cluster = ArrayList<Slot>()
    var clusterEnd = -1
    fun flush() {
        val cols = (cluster.maxOfOrNull { it.col } ?: -1) + 1
        cluster.forEach { out += Block(it.item, it.start, it.end, it.col, cols) }
        cluster = ArrayList()
        clusterEnd = -1
    }
    for (s in slots) {
        if (cluster.isNotEmpty() && s.start >= clusterEnd) flush()
        var col = 0
        while (cluster.any { it.col == col && it.end > s.start }) col++
        s.col = col
        cluster.add(s)
        clusterEnd = max(clusterEnd, s.end)
    }
    flush()
    return out
}

/**
 * Clock-time canvas for one day. Long-press a block and drag to move it; drag the handle at
 * the bottom edge to change its duration. Both snap to 15 minutes. Overlaps sit side by side.
 * Calendar events and reminders are shown but are read-only.
 */
@Composable
fun AgendaView(
    items: List<TimelineItem>,
    date: LocalDate,
    onOpen: (TimelineItem) -> Unit,
    onReschedule: (item: TimelineItem, startMin: Int, durationMin: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allDay = items.filter { !it.hasTime }
    val blocks = remember(items) { layoutBlocks(items) }
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR_HEIGHT.toPx() }
    val scroll = rememberScrollState(with(density) { (HOUR_HEIGHT * 7).roundToPx() })

    Column(modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allDay.forEach { AssistChip(onClick = { onOpen(it) }, label = { Text(it.title, maxLines = 1) }) }
            }
        }
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(HOUR_HEIGHT * 24)) {
                val canvasWidth = maxWidth - GUTTER - 8.dp

                // Hour grid
                for (h in 0..23) {
                    Row(Modifier.offset(y = HOUR_HEIGHT * h).fillMaxWidth().height(HOUR_HEIGHT)) {
                        Text(
                            LocalTime.of(h, 0).let { "%02d:00".format(it.hour) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(GUTTER).padding(start = 8.dp),
                        )
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }

                // Current time line
                if (date == LocalDate.now()) {
                    val now = LocalTime.now()
                    val y = HOUR_HEIGHT * ((now.hour * 60 + now.minute) / 60f)
                    Box(
                        Modifier.offset(x = GUTTER, y = y + 8.dp).width(canvasWidth).height(2.dp)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }

                blocks.forEach { b ->
                    AgendaBlock(b, hourPx, canvasWidth, onOpen, onReschedule)
                }
            }
        }
    }
}

@Composable
private fun AgendaBlock(
    b: Block,
    hourPx: Float,
    canvasWidth: Dp,
    onOpen: (TimelineItem) -> Unit,
    onReschedule: (TimelineItem, Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val draggable = b.item.kind == ItemKind.TASK || b.item.kind == ItemKind.NOTE
    var dy by remember(b.item.id, b.start, b.end) { mutableFloatStateOf(0f) }
    var rdy by remember(b.item.id, b.start, b.end) { mutableFloatStateOf(0f) }

    val colWidth = canvasWidth / b.cols
    val x = GUTTER + colWidth * b.col
    val topPx = b.start / 60f * hourPx + with(density) { 8.dp.toPx() }
    val heightPx = max((b.end - b.start) / 60f * hourPx, hourPx / 4f)
    val duration = b.end - b.start

    fun snap(deltaPx: Float): Int = ((deltaPx / hourPx * 60f) / SNAP_MIN).roundToInt() * SNAP_MIN

    val accent: Color = b.item.color?.let { Color(it) }
        ?: PriorityColors.of(b.item.priority)
        ?: MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .offset { IntOffset(with(density) { x.roundToPx() }, (topPx + dy).roundToInt()) }
            .width(colWidth - 2.dp)
            .height(with(density) { (heightPx + rdy).coerceAtLeast(hourPx / 4f).toDp() })
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = if (b.item.completed) 0.18f else 0.30f))
            .border(1.dp, accent, MaterialTheme.shapes.small)
            .clickable { onOpen(b.item) }
            .then(
                if (draggable) Modifier.pointerInput(b.item.id, b.start, b.end) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, amount -> change.consume(); dy += amount.y },
                        onDragEnd = {
                            val newStart = (b.start + snap(dy)).coerceIn(0, 24 * 60 - duration)
                            dy = 0f
                            if (newStart != b.start) onReschedule(b.item, newStart, duration)
                        },
                        onDragCancel = { dy = 0f },
                    )
                } else Modifier,
            ),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(
                b.item.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (b.end - b.start >= 45) {
                Text(
                    "%02d:%02d–%02d:%02d".format(b.start / 60, b.start % 60, (b.end / 60) % 24, b.end % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (draggable) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp)
                    .pointerInput(b.item.id, b.start, b.end) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount -> change.consume(); rdy += amount },
                            onDragEnd = {
                                val newDur = (duration + snap(rdy)).coerceIn(SNAP_MIN, 24 * 60 - b.start)
                                rdy = 0f
                                if (newDur != duration) onReschedule(b.item, b.start, newDur)
                            },
                            onDragCancel = { rdy = 0f },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(28.dp).height(3.dp).clip(MaterialTheme.shapes.extraSmall).background(accent))
            }
        }
    }
}
