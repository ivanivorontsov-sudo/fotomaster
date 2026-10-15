package org.ivanivorontsov.fotomaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.fotomaster.core.*

fun EditorViewModel.toSrc(o: Offset): PointN {
    val s = source ?: return PointN(o.x, o.y)
    return GeometryOps.outToSource(o.x, o.y, doc.value.geometry, s.w, s.h)
}

fun EditorViewModel.srcToOut(p: PointN): Offset {
    var best = Offset(p.x, p.y); var bd = Float.MAX_VALUE
    val s = source ?: return best
    for (i in 0..20) for (j in 0..20) { val o = Offset(i / 20f, j / 20f); val q = GeometryOps.outToSource(o.x, o.y, doc.value.geometry, s.w, s.h)
        val d = (q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y); if (d < bd) { bd = d; best = o } }
    return best
}

inline fun <reified T : Layer> EditorViewModel.selectedOf(): T? { val id = selectedLayer.value; return doc.value.layers.firstOrNull { it.id == id } as? T }

@Composable
fun BrushSizeSliders(size: Float, onSize: (Float) -> Unit, hard: Float? = null, onHard: (Float) -> Unit = {}) {
    FSlider("Размер кисти", size, 0.005f..0.2f, 0.04f, onSize, {}) { "%.1f%%".format(it * 100) }
    if (hard != null) FSlider("Жёсткость", hard, 0f..1f, 0.7f, onHard, {})
}

@Composable
fun HealPanel(vm: EditorViewModel, doc: Doc, vt: ViewTransform, handler: MutableState<CanvasHandler>) {
    var mode by remember { mutableIntStateOf(0) }
    var size by remember { mutableFloatStateOf(0.025f) }
    var cloneSrc by remember { mutableStateOf<PointN?>(null) }
    fun retouchLayer(): Layer.Retouch = (vm.doc.value.layers.lastOrNull() as? Layer.Retouch) ?: Layer.Retouch(vm.doc.value.nextId())
    fun push(op: RetouchOp, label: String) {
        val l = retouchLayer(); val nl = l.copy(ops = l.ops + op)
        if (vm.doc.value.layers.any { it.id == l.id }) vm.updateLayer(nl, label) else vm.addLayer(nl, label)
    }
    handler.value = object : CanvasHandler {
        override val draws = mode == 0 || cloneSrc != null
        override fun brushRadiusFrac() = size / vt.zoom
        override fun onTap(p: Offset) {
            val s = vm.toSrc(p)
            if (mode == 0) push(RetouchOp.Heal(s.x, s.y, size / vt.zoom), "Точечная ретушь")
            else { cloneSrc = s; vm.message.value = "Источник выбран. Теперь проведите по месту, куда копировать" }
        }
        override fun onStroke(points: List<Offset>) {
            val pts = points.map { vm.toSrc(it) }
            if (mode == 0) {
                val r = size / vt.zoom; val out = mutableListOf(pts.first())
                pts.forEach { if (kotlin.math.hypot(it.x - out.last().x, it.y - out.last().y) > r * 1.2f) out.add(it) }
                val l = retouchLayer(); val nl = l.copy(ops = l.ops + out.map { RetouchOp.Heal(it.x, it.y, r) })
                if (vm.doc.value.layers.any { it.id == l.id }) vm.updateLayer(nl, "Ретушь мазком") else vm.addLayer(nl, "Ретушь мазком")
            } else cloneSrc?.let { src ->
                push(RetouchOp.Clone(src.x - pts.first().x, src.y - pts.first().y, Stroke(pts, size / vt.zoom, hardness = 0.5f)), "Штамп")
            }
        }
        override fun overlay(scope: DrawScope, imgRect: Rect) { val c = cloneSrc ?: return; if (mode != 1) return
            val o = vm.srcToOut(c); scope.drawCircle(Color.Yellow, 16f, Offset(imgRect.left + o.x * imgRect.width, imgRect.top + o.y * imgRect.height), style = DStroke(3f)) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        ChipRow(listOf("Восстанавливающая кисть", "Штамп (клон)"), mode) { mode = it; cloneSrc = null }
        Text(if (mode == 0) "Коснитесь пятна или проведите по нему — область заполнится окружающей текстурой. Двумя пальцами — масштаб." else "1) Коснитесь места-источника. 2) Проведите там, куда копировать.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
        BrushSizeSliders(size, { size = it })
    }
}

@Composable
fun BrushPanel(vm: EditorViewModel, doc: Doc, vt: ViewTransform, handler: MutableState<CanvasHandler>) {
    var erase by remember { mutableStateOf(false) }
    var size by remember { mutableFloatStateOf(0.015f) }
    var hard by remember { mutableFloatStateOf(0.8f) }
    var color by remember { mutableIntStateOf(PALETTE[2]) }
    var alpha by remember { mutableFloatStateOf(1f) }
    handler.value = object : CanvasHandler {
        override val draws = true
        override fun brushRadiusFrac() = size / vt.zoom
        override fun onStroke(points: List<Offset>) {
            val c = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
            val st = Stroke(points.map { vm.toSrc(it) }, size / vt.zoom, c, hard, erase)
            val sel = vm.selectedOf<Layer.Paint>()
            if (sel != null) vm.updateLayer(sel.copy(strokes = sel.strokes + st), if (erase) "Ластик" else "Кисть")
            else if (!erase) vm.addLayer(Layer.Paint(vm.doc.value.nextId(), strokes = listOf(st)), "Новый слой: кисть")
            else vm.message.value = "Ластик стирает рисунок выбранного слоя. Выберите слой во вкладке «Слои»"
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        ChipRow(listOf("Кисть", "Ластик"), if (erase) 1 else 0) { erase = it == 1 }
        ColorRow(color) { color = it }
        BrushSizeSliders(size, { size = it }, hard, { hard = it })
        FSlider("Непрозрачность", alpha, 0.05f..1f, 1f, { alpha = it }, {})
        TextButton({ vm.addLayer(Layer.Paint(vm.doc.value.nextId()), "Новый слой") }) { Text("+ Новый слой рисования") }
    }
}

@Composable
fun TextPanel(vm: EditorViewModel, doc: Doc, handler: MutableState<CanvasHandler>) {
    val sel = vm.selectedOf<Layer.Text>()
    var text by remember(sel?.id) { mutableStateOf(sel?.text ?: "") }
    handler.value = object : CanvasHandler {
        override val draws = false
        override fun onTap(p: Offset) { val s = vm.toSrc(p); vm.selectedOf<Layer.Text>()?.let { vm.updateLayer(it.copy(x = s.x, y = s.y), "Текст перемещён") } }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it; sel?.let { l -> vm.updateLayer(l.copy(text = it)) } }, Modifier.weight(1f).bringIntoViewOnFocus(), label = { Text("Текст") })
            Spacer(Modifier.width(6.dp))
            Button({ if (sel == null && text.isNotBlank()) vm.addLayer(Layer.Text(vm.doc.value.nextId(), text = text), "Добавлен текст") else if (sel != null) vm.commit("Текст", vm.doc.value) }) { Text(if (sel == null) "Добавить" else "Готово") }
        }
        if (sel != null) {
            Text("Коснитесь фото, чтобы переместить текст", fontSize = 11.sp, color = Color.Gray)
            ColorRow(sel.color) { vm.updateLayer(sel.copy(color = it), "Цвет текста") }
            FSlider("Размер", sel.size, 0.01f..0.25f, 0.06f, { vm.updateLayer(sel.copy(size = it)) }, { vm.commit("Размер текста", vm.doc.value) }) { "%.0f".format(it * 1000) }
            Row { FilterChip(sel.bold, { vm.updateLayer(sel.copy(bold = !sel.bold), "Жирный") }, { Text("Жирный") }); Spacer(Modifier.width(6.dp))
                FilterChip(sel.shadow, { vm.updateLayer(sel.copy(shadow = !sel.shadow), "Тень") }, { Text("Тень") }); Spacer(Modifier.width(6.dp))
                TextButton({ vm.selectedLayer.value = null; text = "" }) { Text("Новый текст") } }
        }
    }
}

@Composable
fun BgPanel(vm: EditorViewModel, doc: Doc, vt: ViewTransform, handler: MutableState<CanvasHandler>) {
    val cut = doc.layers.lastOrNull { it is Layer.Cutout } as? Layer.Cutout
    var fix by remember { mutableIntStateOf(-1) }
    var size by remember { mutableFloatStateOf(0.03f) }
    handler.value = object : CanvasHandler {
        override val draws = cut != null && fix >= 0
        override fun brushRadiusFrac() = size / vt.zoom
        override fun onStroke(points: List<Offset>) { val c = cut ?: return
            vm.updateLayer(c.copy(fixStrokes = c.fixStrokes + Stroke(points.map { vm.toSrc(it) }, size / vt.zoom, hardness = 0.6f, erase = fix == 1)), "Правка края") }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        if (cut == null) {
            Text("Удаление фона на телефоне (ML Kit, без интернета). Лучше всего для людей. Для предметов используйте «Нейросети».", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button({ vm.removeBackground(BgMode.TRANSPARENT) }) { Text("Убрать фон") }
                OutlinedButton({ vm.removeBackground(BgMode.BLUR) }) { Text("Размыть фон") }
            }
        } else {
            ChipRow(BgMode.entries.map { it.title }, cut.mode.ordinal) { vm.updateLayer(cut.copy(mode = BgMode.entries[it]), "Фон: ${BgMode.entries[it].title}") }
            if (cut.mode == BgMode.COLOR) ColorRow(cut.color) { vm.updateLayer(cut.copy(color = it), "Цвет фона") }
            ChipRow(listOf("Добавить к объекту", "Убрать из объекта"), fix) { fix = if (fix == it) -1 else it }
            if (fix >= 0) BrushSizeSliders(size, { size = it })
            FSlider("Мягкость края", cut.feather, 0f..1f, 0.3f, { vm.updateLayer(cut.copy(feather = it)) }, { vm.commit("Край", vm.doc.value) })
            Text("PNG сохраняет прозрачность. JPEG заменит прозрачное белым.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
            TextButton({ vm.removeLayer(cut.id) }) { Text("Убрать эффект") }
        }
    }
}

@Composable
fun MaskPanel(vm: EditorViewModel, doc: Doc, vt: ViewTransform, handler: MutableState<CanvasHandler>) {
    val sel = vm.selectedOf<Layer.Local>() ?: doc.layers.lastOrNull { it is Layer.Local } as? Layer.Local
    var paint by remember { mutableIntStateOf(0) }
    var size by remember { mutableFloatStateOf(0.05f) }
    handler.value = object : CanvasHandler {
        override val draws = sel != null && (sel.shape == MaskShape.BRUSH || paint > 0)
        override fun brushRadiusFrac() = size / vt.zoom
        override fun onTap(p: Offset) { val l = sel ?: return; if (l.shape == MaskShape.RADIAL || l.shape == MaskShape.LINEAR) { val s = vm.toSrc(p); vm.updateLayer(l.copy(cx = s.x, cy = s.y), "Центр маски") } }
        override fun onStroke(points: List<Offset>) { val l = sel ?: return
            vm.updateLayer(l.copy(strokes = l.strokes + Stroke(points.map { vm.toSrc(it) }, size / vt.zoom, hardness = 0.4f, erase = paint == 2)), "Маска кистью") }
        override fun overlay(scope: DrawScope, imgRect: Rect) { val l = sel ?: return; if (l.shape != MaskShape.RADIAL && l.shape != MaskShape.LINEAR) return
            val o = vm.srcToOut(PointN(l.cx, l.cy)); scope.drawCircle(Color.White, 12f, Offset(imgRect.left + o.x * imgRect.width, imgRect.top + o.y * imgRect.height), style = DStroke(3f)) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        Text("Выборочная коррекция: создайте маску и двигайте ползунки только для неё.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
        FlowRowCompat {
            MaskShape.entries.forEach { s -> AssistChip({ if (s == MaskShape.PERSON) vm.personMaskLayer() else vm.addLayer(Layer.Local(vm.doc.value.nextId(), name = "Маска: ${s.title}", shape = s), "Маска «${s.title}»") }, { Text("+ ${s.title}") }) }
        }
        if (sel != null) {
            Text("Маска: ${sel.name}", color = Accent, modifier = Modifier.padding(start = 12.dp))
            if (sel.shape != MaskShape.BRUSH) ChipRow(listOf("Без кисти", "Дорисовать маску", "Стереть маску"), paint) { paint = it }
            if (sel.shape == MaskShape.BRUSH || paint > 0) BrushSizeSliders(size, { size = it })
            if (sel.shape == MaskShape.RADIAL) FSlider("Радиус", sel.radius, 0.02f..1f, 0.3f, { vm.updateLayer(sel.copy(radius = it)) }, { vm.commit("Радиус маски", vm.doc.value) })
            if (sel.shape == MaskShape.LINEAR) FSlider("Угол", sel.angle, 0f..360f, 90f, { vm.updateLayer(sel.copy(angle = it)) }, { vm.commit("Угол маски", vm.doc.value) }) { "%.0f°".format(it) }
            if (sel.shape == MaskShape.RADIAL || sel.shape == MaskShape.LINEAR) FSlider("Растушёвка", sel.feather, 0.02f..1f, 0.5f, { vm.updateLayer(sel.copy(feather = it)) }, { vm.commit("Растушёвка", vm.doc.value) })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) { Checkbox(sel.invert, { vm.updateLayer(sel.copy(invert = it), "Инверсия маски") }); Text("Инвертировать") }
            listOf<Triple<String, (Adjustments) -> Float, (Adjustments, Float) -> Adjustments>>(
                Triple("Экспозиция", { it.exposure }, { a, v -> a.copy(exposure = v) }), Triple("Контраст", { it.contrast }, { a, v -> a.copy(contrast = v) }),
                Triple("Тени", { it.shadows }, { a, v -> a.copy(shadows = v) }), Triple("Светлые", { it.highlights }, { a, v -> a.copy(highlights = v) }),
                Triple("Температура", { it.temperature }, { a, v -> a.copy(temperature = v) }), Triple("Насыщенность", { it.saturation }, { a, v -> a.copy(saturation = v) }),
                Triple("Чёткость", { it.clarity }, { a, v -> a.copy(clarity = v) }), Triple("Резкость", { it.sharpen }, { a, v -> a.copy(sharpen = v.coerceAtLeast(0f)) }),
                Triple("Сглаживание", { it.denoise }, { a, v -> a.copy(denoise = v.coerceAtLeast(0f)) }),
            ).forEach { (label, get, set) ->
                FSlider(label, get(sel.adj), onChange = { v -> (vm.doc.value.layers.firstOrNull { it.id == sel.id } as? Layer.Local)?.let { cur -> vm.updateLayer(cur.copy(adj = set(cur.adj, v))) } }, onDone = { vm.commit("Маска: $label", vm.doc.value) })
            }
        }
    }
}

@Composable
fun LayersPanel(vm: EditorViewModel, doc: Doc, vt: ViewTransform, handler: MutableState<CanvasHandler>) {
    val selId by vm.selectedLayer.collectAsState()
    var size by remember { mutableFloatStateOf(0.04f) }
    val selPic = doc.layers.firstOrNull { it.id == selId } as? Layer.Picture
    handler.value = object : CanvasHandler {
        override val draws = selPic != null
        override fun brushRadiusFrac() = size / vt.zoom
        override fun onStroke(points: List<Offset>) { val p = selPic ?: return
            vm.updateLayer(p.copy(eraseStrokes = p.eraseStrokes + Stroke(points.map { vm.toSrc(it) }, size / vt.zoom, hardness = 0.5f)), "Стирание слоя") }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(6.dp)) {
        if (doc.layers.isEmpty()) Text("Слоёв пока нет. Они появляются при ретуши, рисовании, тексте, масках, удалении фона и результатах нейросетей. Базовое фото и ползунки — нижний неразрушающий слой.", fontSize = 12.sp, color = Color.Gray)
        doc.layers.reversed().forEach { l ->
            Row(Modifier.fillMaxWidth().background(if (l.id == selId) Color(0x334FC3F7) else Color.Transparent).clickable { vm.selectedLayer.value = if (selId == l.id) null else l.id }.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.updateLayer(l.withVisibility(!l.visible), if (l.visible) "Скрыт слой" else "Показан слой") }) { Icon(if (l.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Видимость") }
                Text(l.name, Modifier.weight(1f), fontSize = 14.sp)
                IconButton({ vm.moveLayer(l.id, 1) }) { Icon(Icons.Default.ArrowUpward, "Выше") }
                IconButton({ vm.moveLayer(l.id, -1) }) { Icon(Icons.Default.ArrowDownward, "Ниже") }
                IconButton({ vm.removeLayer(l.id) }) { Icon(Icons.Default.Delete, "Удалить") }
            }
        }
        doc.layers.firstOrNull { it.id == selId }?.let { l ->
            FSlider("Непрозрачность слоя", l.opacity, 0f..1f, 1f, { vm.updateLayer(l.withOpacity(it)) }, { vm.commit("Непрозрачность", vm.doc.value) })
            when (l) {
                is Layer.Paint -> ChipRow(Blend.entries.map { it.title }, l.blend.ordinal) { vm.updateLayer(l.copy(blend = Blend.entries[it]), "Режим наложения") }
                is Layer.Picture -> { ChipRow(Blend.entries.map { it.title }, l.blend.ordinal) { vm.updateLayer(l.copy(blend = Blend.entries[it]), "Режим наложения") }
                    Text("Проведите по фото, чтобы стереть часть этого слоя (например, оставить ИИ-правку только на лице).", fontSize = 11.sp, color = Color.Gray)
                    BrushSizeSliders(size, { size = it }) }
                else -> {}
            }
        }
    }
}
