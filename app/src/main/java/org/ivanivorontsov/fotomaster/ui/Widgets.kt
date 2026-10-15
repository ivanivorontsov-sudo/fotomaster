package org.ivanivorontsov.fotomaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

@Composable
fun FSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = -1f..1f, default: Float = 0f, onChange: (Float) -> Unit, onDone: () -> Unit, fmt: (Float) -> String = { ((it * 100).roundToInt()).toString() }) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (value != default) Text("↺ ", color = Accent, fontSize = 14.sp, modifier = Modifier.clickable { onChange(default); onDone() })
            Text(fmt(value), fontSize = 13.sp, color = if (value != default) Accent else Color.Gray)
        }
        Slider(value, onValueChange = onChange, valueRange = range, onValueChangeFinished = onDone, modifier = Modifier.height(28.dp))
    }
}

@Composable
fun ChipRow(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScrollCompat().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, s -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(s, fontSize = 12.sp) }) }
    }
}

@Composable
fun Modifier.horizontalScrollCompat(): Modifier = this.horizontalScroll(androidx.compose.foundation.rememberScrollState())

val PALETTE = listOf(0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFFB300, 0xFFFFEB3B, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFF06292, 0xFF795548).map { it.toInt() }

@Composable
fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScrollCompat().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { c ->
            Box(Modifier.size(30.dp).clip(CircleShape).background(if (c == selected) Accent else Color.DarkGray).padding(3.dp).clip(CircleShape).background(Color(c)).clickable { onPick(c) })
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Modifier.bringIntoViewOnFocus(): Modifier {
    val req = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, imeVisible) { if (focused) { kotlinx.coroutines.delay(250); req.bringIntoView() } }
    return this.bringIntoViewRequester(req).onFocusChanged { focused = it.isFocused }
}

@androidx.compose.runtime.Composable
fun ScrollHintBox(state: androidx.compose.foundation.lazy.LazyListState, tag: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val canLeft by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { state.canScrollBackward } }
    val canRight by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { state.canScrollForward } }
    androidx.compose.runtime.LaunchedEffect(canRight) {
        val prefs = ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
        if (canRight && !prefs.getBoolean("nudge_$tag", false)) {
            prefs.edit().putBoolean("nudge_$tag", true).apply()
            kotlinx.coroutines.delay(600); state.animateScrollBy(120f); kotlinx.coroutines.delay(250); state.animateScrollBy(-120f)
        }
    }
    val bg = androidx.compose.ui.graphics.Color(0xFF15171C)
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth()) {
        content()
        listOf(false to canLeft, true to canRight).forEach { (right, show) ->
            androidx.compose.animation.AnimatedVisibility(show, androidx.compose.ui.Modifier.align(if (right) androidx.compose.ui.Alignment.CenterEnd else androidx.compose.ui.Alignment.CenterStart),
                enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                val stops = if (right) listOf(androidx.compose.ui.graphics.Color.Transparent, bg) else listOf(bg, androidx.compose.ui.graphics.Color.Transparent)
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(44.dp).height(60.dp)
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(stops))
                    .clickable { scope.launch { state.animateScrollBy(if (right) 240f * ctx.resources.displayMetrics.density else -240f * ctx.resources.displayMetrics.density) } }
                    .testTag(if (right) "${tag}_more_right" else "${tag}_more_left"),
                    contentAlignment = if (right) androidx.compose.ui.Alignment.CenterEnd else androidx.compose.ui.Alignment.CenterStart) {
                    androidx.compose.material3.Text(if (right) "›" else "‹", androidx.compose.ui.Modifier.padding(horizontal = 6.dp), fontSize = 28.sp, color = Accent)
                }
            }
        }
    }
}
