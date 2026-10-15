package org.ivanivorontsov.fotomaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
