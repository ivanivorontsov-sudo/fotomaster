package org.ivanivorontsov.fotomaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.fotomaster.core.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ToolPanel(tool: Tool, vm: EditorViewModel, vt: ViewTransform, handler: MutableState<CanvasHandler>, showWeb: Boolean, onToggleWeb: () -> Unit, onAccounts: () -> Unit) {
    val doc by vm.doc.collectAsState()
    if (tool !in setOf(Tool.BRUSH, Tool.HEAL, Tool.TEXT, Tool.BG, Tool.MASK, Tool.CROP, Tool.LAYERS)) handler.value = NoHandler
    val scroll = Modifier.verticalScroll(rememberScrollState())
    when (tool) {
        Tool.AUTO -> Column(scroll.padding(12.dp)) {
            Button(vm::autoEnhance, Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text("Автоулучшение") }
            Text("Анализ гистограммы, баланса белого, шума и лиц прямо на телефоне. Результат — обычные ползунки, всё можно подправить или отменить.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.commit("Сброс правок", doc.copy(adj = Adjustments())) }) { Text("Сбросить цвет/свет") }
                OutlinedButton({ vm.commit("Сброс всего", Doc()) }) { Text("Сбросить всё") }
            }
        }
        Tool.PRESETS -> PresetsPanel(vm, doc)
        Tool.CROP -> CropPanel(vm, doc, handler)
        Tool.LIGHT -> AdjPanel(vm, doc.adj, listOf(
            Triple("Экспозиция", { a: Adjustments -> a.exposure }, { a: Adjustments, v: Float -> a.copy(exposure = v) }),
            Triple("Контраст", { a -> a.contrast }, { a, v -> a.copy(contrast = v) }),
            Triple("Светлые", { a -> a.highlights }, { a, v -> a.copy(highlights = v) }),
            Triple("Тени", { a -> a.shadows }, { a, v -> a.copy(shadows = v) }),
            Triple("Белые", { a -> a.whites }, { a, v -> a.copy(whites = v) }),
            Triple("Чёрные", { a -> a.blacks }, { a, v -> a.copy(blacks = v) }),
            Triple("Виньетка", { a -> a.vignette }, { a, v -> a.copy(vignette = v) }),
        ), extra = { LevelsBlock(vm, doc.adj) })
        Tool.COLOR -> AdjPanel(vm, doc.adj, listOf(
            Triple("Температура (баланс белого)", { a: Adjustments -> a.temperature }, { a: Adjustments, v: Float -> a.copy(temperature = v) }),
            Triple("Оттенок", { a -> a.tint }, { a, v -> a.copy(tint = v) }),
            Triple("Насыщенность", { a -> a.saturation }, { a, v -> a.copy(saturation = v) }),
            Triple("Красочность", { a -> a.vibrance }, { a, v -> a.copy(vibrance = v) }),
        ))
        Tool.HSL -> HslPanel(vm, doc.adj)
        Tool.CURVES -> CurvesPanel(vm, doc.adj)
        Tool.DETAIL -> AdjPanel(vm, doc.adj, listOf(
            Triple("Резкость", { a: Adjustments -> a.sharpen }, { a: Adjustments, v: Float -> a.copy(sharpen = v.coerceAtLeast(0f)) }),
            Triple("Чёткость (локальный контраст)", { a -> a.clarity }, { a, v -> a.copy(clarity = v) }),
            Triple("Шумоподавление", { a -> a.denoise }, { a, v -> a.copy(denoise = v.coerceAtLeast(0f)) }),
        ))
        Tool.HEAL -> HealPanel(vm, doc, vt, handler)
        Tool.BRUSH -> BrushPanel(vm, doc, vt, handler)
        Tool.TEXT -> TextPanel(vm, doc, handler)
        Tool.BG -> BgPanel(vm, doc, vt, handler)
        Tool.MASK -> MaskPanel(vm, doc, vt, handler)
        Tool.LAYERS -> LayersPanel(vm, doc, vt, handler)
        Tool.AI -> AiPanel(vm, showWeb, onToggleWeb, onAccounts)
        Tool.HISTORY -> HistoryPanel(vm)
    }
}

@Composable
fun AdjPanel(vm: EditorViewModel, adj: Adjustments, items: List<Triple<String, (Adjustments) -> Float, (Adjustments, Float) -> Adjustments>>, extra: @Composable () -> Unit = {}) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        items.forEach { (label, get, set) ->
            FSlider(label, get(adj), onChange = { vm.setAdj(set(vm.doc.value.adj, it)) }, onDone = { vm.commit(label, vm.doc.value) })
        }
        extra()
    }
}

@Composable
fun LevelsBlock(vm: EditorViewModel, adj: Adjustments) {
    val lv = adj.levels
    Text("Уровни", Modifier.padding(start = 12.dp, top = 8.dp), color = Accent, fontSize = 13.sp)
    fun set(l: Levels) = vm.setAdj(vm.doc.value.adj.copy(levels = l))
    val done = { vm.commit("Уровни", vm.doc.value) }
    FSlider("Вход: чёрная точка", lv.inBlack.toFloat(), 0f..200f, 0f, { set(lv.copy(inBlack = it.roundToInt())) }, done) { it.roundToInt().toString() }
    FSlider("Вход: белая точка", lv.inWhite.toFloat(), 55f..255f, 255f, { set(lv.copy(inWhite = it.roundToInt())) }, done) { it.roundToInt().toString() }
    FSlider("Гамма", lv.gamma, 0.3f..3f, 1f, { set(lv.copy(gamma = it)) }, done) { "%.2f".format(it) }
    FSlider("Выход: чёрный", lv.outBlack.toFloat(), 0f..120f, 0f, { set(lv.copy(outBlack = it.roundToInt())) }, done) { it.roundToInt().toString() }
    FSlider("Выход: белый", lv.outWhite.toFloat(), 135f..255f, 255f, { set(lv.copy(outWhite = it.roundToInt())) }, done) { it.roundToInt().toString() }
}

@Composable
fun HslPanel(vm: EditorViewModel, adj: Adjustments) {
    var band by remember { mutableIntStateOf(0) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        ChipRow(Adjustments.HUE_NAMES, band) { band = it }
        listOf("Тон", "Насыщенность", "Яркость").forEachIndexed { k, label ->
            FSlider(label, adj.hsl[band * 3 + k], onChange = { v -> vm.setAdj(vm.doc.value.adj.let { a -> a.copy(hsl = a.hsl.toMutableList().also { it[band * 3 + k] = v }) }) },
                onDone = { vm.commit("HSL: ${Adjustments.HUE_NAMES[band]}", vm.doc.value) })
        }
    }
}

@Composable
fun CurvesPanel(vm: EditorViewModel, adj: Adjustments) {
    var drag by remember { mutableIntStateOf(-1) }
    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Тап — добавить точку, тяните — двигать, двойной тап по точке — удалить", fontSize = 11.sp, color = Color.Gray)
        Box(Modifier.size(210.dp).background(Color(0xFF0B0C0F))) {
            Canvas(Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(
                    onDoubleTap = { o -> val pts = vm.doc.value.adj.curve; val i = nearest(pts, o, size.width.toFloat(), size.height.toFloat())
                        if (i > 0 && i < pts.size - 1) vm.commit("Кривая", vm.doc.value.copy(adj = vm.doc.value.adj.copy(curve = pts.filterIndexed { j, _ -> j != i }))) },
                    onTap = { o -> val pts = vm.doc.value.adj.curve; val x = (o.x / size.width).coerceIn(0.01f, 0.99f); val y = 1f - (o.y / size.height).coerceIn(0f, 1f)
                        if (nearest(pts, o, size.width.toFloat(), size.height.toFloat()) < 0) vm.commit("Кривая", vm.doc.value.copy(adj = vm.doc.value.adj.copy(curve = (pts + (x to y)).sortedBy { it.first }))) }) }
                .pointerInput(Unit) { detectDragGestures(
                    onDragStart = { o -> drag = nearest(vm.doc.value.adj.curve, o, size.width.toFloat(), size.height.toFloat()) },
                    onDragEnd = { if (drag >= 0) vm.commit("Кривая", vm.doc.value); drag = -1 },
                    onDrag = { ch, _ -> if (drag < 0) return@detectDragGestures
                        val pts = vm.doc.value.adj.curve.toMutableList(); val p = ch.position
                        val lo = if (drag == 0) 0f else pts[drag - 1].first + 0.01f; val hi = if (drag == pts.size - 1) 1f else pts[drag + 1].first - 0.01f
                        val x = if (drag == 0) 0f else if (drag == pts.size - 1) 1f else (p.x / size.width).coerceIn(lo, hi)
                        pts[drag] = x to (1f - p.y / size.height).coerceIn(0f, 1f)
                        vm.setAdj(vm.doc.value.adj.copy(curve = pts)) }) }) {
                for (i in 1..3) { drawLine(Color.DarkGray, Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height)); drawLine(Color.DarkGray, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4)) }
                val lut = Curve.lut(adj.curve)
                val path = Path().apply { for (v in 0..255) { val x = v / 255f * size.width; val y = size.height - lut[v] / 255f * size.height; if (v == 0) moveTo(x, y) else lineTo(x, y) } }
                drawPath(path, Color.White, style = DStroke(3f))
                adj.curve.forEach { drawCircle(Accent, 9f, Offset(it.first * size.width, size.height - it.second * size.height)) }
            }
        }
        TextButton({ vm.commit("Кривая сброшена", vm.doc.value.copy(adj = vm.doc.value.adj.copy(curve = Adjustments.IDENTITY_CURVE))) }) { Text("Сбросить кривую") }
    }
}

private fun nearest(pts: List<Pair<Float, Float>>, o: Offset, w: Float, h: Float): Int {
    var best = -1; var bd = 40f
    pts.forEachIndexed { i, p -> val d = abs(p.first * w - o.x) + abs((1 - p.second) * h - o.y); if (d < bd) { bd = d; best = i } }
    return best
}

@Composable
fun PresetsPanel(vm: EditorViewModel, doc: Doc) {
    val user by vm.userPresets.collectAsState()
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text("Касание — применить (заменяет свет и цвет; слои не трогает). Всё обратимо.", fontSize = 11.sp, color = Color.Gray)
        FlowRowCompat { (Presets.builtIn + user).forEach { p ->
            AssistChip(onClick = { vm.applyPreset(p) }, label = { Text(p.name) }, trailingIcon = if (!p.builtIn) ({ Icon(Icons.Default.Close, "Удалить", Modifier.size(16.dp).clickable { vm.deletePreset(p) }) }) else null)
        } }
        OutlinedButton({ naming = true }) { Icon(Icons.Default.Add, null); Text("Сохранить текущие настройки как пресет") }
    }
    if (naming) AlertDialog(onDismissRequest = { naming = false }, title = { Text("Название пресета") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton({ if (name.isNotBlank()) vm.savePreset(name.trim()); naming = false; name = "" }) { Text("Сохранить") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowCompat(content: @Composable () -> Unit) = FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }

@Composable
fun CropPanel(vm: EditorViewModel, doc: Doc, handler: MutableState<CanvasHandler>) {
    val ratios = listOf("Свободно" to 0f, "1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "9:16" to 9f / 16, "16:9" to 16f / 9, "3:2" to 1.5f)
    var ratio by remember { mutableIntStateOf(0) }
    val src = vm.source
    val g = doc.geometry
    val imgAspect = remember(g.rot90, src) { if (src == null) 1f else if (g.rot90 % 2 == 1) src.h.toFloat() / src.w else src.w.toFloat() / src.h }
    handler.value = remember(doc.geometry, ratio) { object : CanvasHandler {
        override val draws = false
        var startRect = g.crop; var mode = -1
        override fun onDrag(start: Offset, cur: Offset, phase: Int): Boolean {
            val r = vm.doc.value.geometry.crop
            if (phase == 0) { startRect = r
                val corners = listOf(Offset(r.l, r.t), Offset(r.r, r.t), Offset(r.l, r.b), Offset(r.r, r.b))
                mode = corners.indexOfFirst { (it - start).getDistance() < 0.08f }
                if (mode < 0 && start.x in r.l..r.r && start.y in r.t..r.b) mode = 4
                return mode >= 0 }
            val d = cur - start; val s = startRect
            var n = when (mode) {
                0 -> s.copy(l = (s.l + d.x).coerceIn(0f, s.r - 0.05f), t = (s.t + d.y).coerceIn(0f, s.b - 0.05f))
                1 -> s.copy(r = (s.r + d.x).coerceIn(s.l + 0.05f, 1f), t = (s.t + d.y).coerceIn(0f, s.b - 0.05f))
                2 -> s.copy(l = (s.l + d.x).coerceIn(0f, s.r - 0.05f), b = (s.b + d.y).coerceIn(s.t + 0.05f, 1f))
                3 -> s.copy(r = (s.r + d.x).coerceIn(s.l + 0.05f, 1f), b = (s.b + d.y).coerceIn(s.t + 0.05f, 1f))
                else -> { val dx = d.x.coerceIn(-s.l, 1f - s.r); val dy = d.y.coerceIn(-s.t, 1f - s.b); RectN(s.l + dx, s.t + dy, s.r + dx, s.b + dy) }
            }
            val want = ratios[ratio].second
            if (want > 0f && mode in 0..3) { val wN = n.r - n.l; val hN = wN * imgAspect / want
                n = if (mode < 2) n.copy(t = (n.b - hN).coerceAtLeast(0f)) else n.copy(b = (n.t + hN).coerceAtMost(1f)) }
            val d2 = vm.doc.value.copy(geometry = vm.doc.value.geometry.copy(crop = n))
            if (phase == 2) vm.commit("Кадрирование", d2) else vm.preview(d2)
            return true
        }
        override fun overlay(scope: DrawScope, imgRect: Rect) = with(scope) {
            val r = vm.doc.value.geometry.crop
            val cr = Rect(imgRect.left + r.l * imgRect.width, imgRect.top + r.t * imgRect.height, imgRect.left + r.r * imgRect.width, imgRect.top + r.b * imgRect.height)
            val shade = Color(0x99000000)
            drawRect(shade, imgRect.topLeft, androidx.compose.ui.geometry.Size(imgRect.width, cr.top - imgRect.top))
            drawRect(shade, Offset(imgRect.left, cr.bottom), androidx.compose.ui.geometry.Size(imgRect.width, imgRect.bottom - cr.bottom))
            drawRect(shade, Offset(imgRect.left, cr.top), androidx.compose.ui.geometry.Size(cr.left - imgRect.left, cr.height))
            drawRect(shade, Offset(cr.right, cr.top), androidx.compose.ui.geometry.Size(imgRect.right - cr.right, cr.height))
            drawRect(Color.White, cr.topLeft, cr.size, style = DStroke(3f))
            for (i in 1..2) { drawLine(Color(0x88FFFFFF), Offset(cr.left + cr.width * i / 3, cr.top), Offset(cr.left + cr.width * i / 3, cr.bottom)); drawLine(Color(0x88FFFFFF), Offset(cr.left, cr.top + cr.height * i / 3), Offset(cr.right, cr.top + cr.height * i / 3)) }
            listOf(cr.topLeft, cr.topRight, cr.bottomLeft, cr.bottomRight).forEach { drawCircle(Color.White, 14f, it) }
        }
    } }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        ChipRow(ratios.map { it.first }, ratio) { i -> ratio = i
            val want = ratios[i].second
            if (want > 0f) { val c = vm.doc.value.geometry.crop; val w = c.r - c.l; var hN = w * imgAspect / want; var wN = w
                if (hN > 1f) { hN = 1f; wN = want / imgAspect }
                val cx = (c.l + c.r) / 2; val cy = (c.t + c.b) / 2
                val n = RectN((cx - wN / 2).coerceIn(0f, 1f - wN), (cy - hN / 2).coerceIn(0f, 1f - hN), 0f, 0f).let { it.copy(r = it.l + wN, b = it.t + hN) }
                vm.commit("Пропорции ${ratios[i].first}", vm.doc.value.copy(geometry = vm.doc.value.geometry.copy(crop = n))) } }
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton({ vm.commit("Поворот", doc.copy(geometry = g.copy(rot90 = (g.rot90 + 3) % 4, crop = RectN()))) }) { Icon(Icons.Default.RotateLeft, null) }
            OutlinedButton({ vm.commit("Поворот", doc.copy(geometry = g.copy(rot90 = (g.rot90 + 1) % 4, crop = RectN()))) }) { Icon(Icons.Default.RotateRight, null) }
            OutlinedButton({ vm.commit("Отражение", doc.copy(geometry = g.copy(flipH = !g.flipH))) }) { Icon(Icons.Default.Flip, null) }
            OutlinedButton({ vm.commit("Кадр сброшен", doc.copy(geometry = Geometry())) }) { Text("Сброс") }
        }
        FSlider("Выравнивание горизонта", g.straighten, -45f..45f, 0f, { vm.preview(vm.doc.value.copy(geometry = vm.doc.value.geometry.copy(straighten = it))) }, { vm.commit("Выравнивание", vm.doc.value) }) { "%.1f°".format(it) }
    }
}

@Composable
fun HistoryPanel(vm: EditorViewModel) {
    val entries by vm.historyEntries.collectAsState()
    val idx by vm.historyIndex.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text("Полная история правок. Касание — вернуться к шагу (последующие шаги не теряются, пока вы не сделаете новую правку).", fontSize = 11.sp, color = Color.Gray)
        entries.forEachIndexed { i, e ->
            Text("${i + 1}. $e", Modifier.fillMaxWidth().clickable { vm.jump(i) }.background(if (i == idx) Color(0x334FC3F7) else Color.Transparent).padding(8.dp),
                color = if (i > idx) Color.Gray else Color.White)
        }
    }
}
