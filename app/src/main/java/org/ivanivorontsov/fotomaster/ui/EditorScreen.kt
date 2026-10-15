package org.ivanivorontsov.fotomaster.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tool(val title: String, val icon: ImageVector) {
    AUTO("Авто", Icons.Default.AutoFixHigh), PRESETS("Пресеты", Icons.Default.Style), CROP("Кадр", Icons.Default.Crop),
    LIGHT("Свет", Icons.Default.WbSunny), COLOR("Цвет", Icons.Default.Palette), HSL("HSL", Icons.Default.ColorLens),
    CURVES("Кривые", Icons.Default.ShowChart), DETAIL("Детали", Icons.Default.Grain), HEAL("Ретушь", Icons.Default.Healing),
    BRUSH("Кисть", Icons.Default.Brush), TEXT("Текст", Icons.Default.TextFields), BG("Фон", Icons.Default.PersonOutline),
    MASK("Маски", Icons.Default.Gradient), LAYERS("Слои", Icons.Default.Layers), AI("Нейросети", Icons.Default.AutoAwesome),
    HISTORY("История", Icons.Default.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, showWeb: Boolean, onToggleWeb: () -> Unit, onAccounts: () -> Unit) {
    val bm by vm.rendered.collectAsState()
    val before by vm.before.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    var tool by remember { mutableStateOf(Tool.AUTO) }
    var holdBefore by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf<Float?>(null) }
    var showExport by remember { mutableStateOf(false) }
    val vt = remember { ViewTransform() }
    val handler = remember { mutableStateOf<CanvasHandler>(NoHandler) }
    LaunchedEffect(tool) { vm.renderNoCrop.value = tool == Tool.CROP; if (tool != Tool.CROP) vm.cancelPreview() }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            IconButton(vm::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Отменить") }
            IconButton(vm::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Повторить") }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(48.dp).pointerInput(Unit) { detectTapGestures(onPress = { holdBefore = true; tryAwaitRelease(); holdBefore = false }) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Compare, "Держите: до/после", tint = if (holdBefore) Accent else Color.White)
            }
            IconButton({ split = if (split == null) 0.5f else null }) { Icon(Icons.Default.VerticalSplit, "Шторка до/после", tint = if (split != null) Accent else Color.White) }
            IconButton({ vt.reset() }) { Icon(Icons.Default.FitScreen, "Вписать") }
            FilledTonalIconButton({ showExport = true }, Modifier.padding(end = 6.dp)) { Icon(Icons.Default.Save, "Сохранить") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF08090B))) {
            ImageCanvas(bm, before, holdBefore, split, vt, handler.value)
            if (split != null) Slider(split!!, { split = it }, Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp))
            if (holdBefore) Text("ДО", Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xAA000000)).padding(6.dp), fontSize = 12.sp)
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Box(Modifier.heightIn(max = 300.dp)) {
                    ToolPanel(tool, vm, vt, handler, showWeb, onToggleWeb, onAccounts)
                }
                LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    itemsIndexed(Tool.entries) { _, t ->
                        Column(Modifier.width(70.dp).padding(2.dp).pointerInput(t) { detectTapGestures { tool = t } }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(t.icon, t.title, tint = if (t == tool) Accent else Color.LightGray)
                            Text(t.title, fontSize = 11.sp, color = if (t == tool) Accent else Color.LightGray, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    if (showExport) ExportDialog(vm, onDismiss = { showExport = false })
    BusyAndMessages(busy, msg) { vm.message.value = null }
}
