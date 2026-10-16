package org.ivanivorontsov.fotomaster.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.fotomaster.ai.PromptSession
import org.ivanivorontsov.fotomaster.core.BgMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptTab(vm: EditorViewModel, onOpenSite: () -> Unit) {
    val tick by vm.promptTick.collectAsState()
    val aiStatus by vm.ai.status.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importResult(u) }
    val current by vm.rendered.collectAsState()
    val s = vm.prompt
    var text by remember(tick == 0) { mutableStateOf(s.prompt) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var holdBefore by remember { mutableStateOf(false) }
    val vt = remember { ViewTransform() }
    val provider = vm.providers.first { it.id == s.providerId }
    val selected = remember(tick) { s.selected }
    val running = false
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF08090B)).testTag("prompt_preview")) {
            if (selected != null) {
                val before = vm.promptBeforeOf(selected.id)
                ImageCanvas(selected.image, before?.let { b -> android.graphics.Bitmap.createScaledBitmap(b, selected.width, selected.height, true) }, holdBefore, split, vt, NoHandler)
                Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("ДО"); Label("ПОСЛЕ · ${selected.providerTitle} · ${selected.width}×${selected.height}")
                }
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp).pointerInput(Unit) { detectTapGestures(onPress = { holdBefore = true; tryAwaitRelease(); holdBefore = false }) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Compare, "Держите: оригинал", tint = if (holdBefore) Accent else Color.White)
                }
                Slider(split, { split = it }, Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp))
            } else {
                ImageCanvas(current, null, false, null, vt, NoHandler)
                Text("Результат из нейросети появится здесь, рядом с оригиналом", Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Color(0xAA000000)).padding(6.dp), fontSize = 12.sp)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) { Column {
            Column(Modifier.heightIn(max = if (androidx.compose.foundation.layout.WindowInsets.isImeVisible) 90.dp else 230.dp).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (selected != null && !running) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button({ vm.applyPromptResult(selected.id) }, Modifier.weight(1f).testTag("btn_apply"), enabled = !selected.applied, contentPadding = PaddingValues(4.dp)) { Text(if (selected.applied) "Применено" else "Применить", fontSize = 13.sp) }
                    OutlinedButton({ vm.openAi(); onOpenSite() }, Modifier.weight(1f).testTag("btn_retry"), contentPadding = PaddingValues(4.dp)) { Text("Ещё раз", fontSize = 13.sp) }
                    OutlinedButton({ vm.savePromptResult(selected.id) }, Modifier.weight(1.3f).testTag("btn_save"), contentPadding = PaddingValues(4.dp)) { Text(if (selected.saved) "Сохранено" else "Сохранить как есть", fontSize = 13.sp, maxLines = 1) }
                }
                if (s.results.isNotEmpty()) {
                    Text("История промптов (${s.results.size})", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("prompt_history")) {
                        items(s.results, key = { it.id }) { r ->
                            Column(Modifier.width(76.dp).clickable { s.select(r.id); text = r.prompt; vm.promptTick.value++ }) {
                                Image(r.image.asImageBitmap(), null, Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)).border(BorderStroke(2.dp, if (r.id == selected?.id) Accent else Color.Transparent), RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Text(r.prompt, fontSize = 9.sp, maxLines = 2, color = Color.LightGray)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    vm.providers.forEach { p ->
                        FilterChip(p.id == s.providerId, { vm.selectProvider(p.id) }, { Text(p.title) }, Modifier.padding(end = 6.dp).testTag("provider_${p.id}"))
                    }
                }
                Text("Как это работает: 1) нажмите «Открыть ${provider.title}» — запрос скопируется; 2) на сайте сами прикрепите фото, вставьте запрос и нажмите «Отправить»; 3) долго нажмите на готовую картинку и выберите «Взять в ФотоМастер» или сохраните её и загрузите из галереи.", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.testTag("ai_howto"))
                Text("${provider.title}: ${provider.hint}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("prompt_chips")) {
                    PromptSession.QUICK.forEachIndexed { i, (title, full) ->
                        SuggestionChip({ s.applyChip(i); text = full; vm.promptTick.value++ }, { Text(title, fontSize = 12.sp) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton({ if (vm.copyPrompt()) android.widget.Toast.makeText(ctx, "Запрос скопирован", android.widget.Toast.LENGTH_SHORT).show() }, Modifier.testTag("btn_copy")) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Копировать запрос", fontSize = 12.sp) }
                    OutlinedButton({ vm.savePhotoForAi() }, Modifier.testTag("btn_save_photo")) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(4.dp)); Text("Сохранить фото для отправки", fontSize = 12.sp) }
                    OutlinedButton({ importer.launch("image/*") }, Modifier.testTag("btn_import")) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(4.dp)); Text("Загрузить результат из галереи", fontSize = 12.sp) }
                    TextButton({ runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(provider.startUrl))) } }, Modifier.testTag("btn_browser")) { Text("Открыть в браузере", fontSize = 12.sp) }
                }
                aiStatus?.let { Text(it, fontSize = 12.sp, color = Accent) }
                Text("Приложение ничего не нажимает и не отправляет за вас: фото и запрос отправляете вы сами на сайте сервиса, по его правилам.", fontSize = 10.sp, color = Color.Gray)
            }
            Column(Modifier.padding(horizontal = 10.dp).padding(bottom = 6.dp)) {
                s.error?.let { f ->
                    var showLog by remember(f) { mutableStateOf(false) }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0x33EF5350)), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("ai_error")) {
                        Column(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${f.provider}: не получилось", Modifier.weight(1f), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                                IconButton({ vm.dismissAiError() }, Modifier.size(36.dp).testTag("btn_error_close")) { Icon(Icons.Default.Close, "Закрыть") }
                            }
                            Text(f.message, fontSize = 12.sp, maxLines = 3)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ vm.dismissAiError(); vm.openAi(); onOpenSite() }, Modifier.testTag("btn_error_retry")) { Text("Ещё раз") }
                                if (f.log.isNotEmpty()) TextButton({ showLog = !showLog }, Modifier.testTag("btn_log")) { Text(if (showLog) "Скрыть журнал" else "Журнал (${f.log.size})") }
                                TextButton({ vm.dismissAiError(); vm.autoEnhance() }) { Text("Автоулучшение") }
                            }
                            if (showLog) Text(f.log.takeLast(40).joinToString("\n"), Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()).testTag("ai_log"), fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                }
                OutlinedTextField(text, { text = it; s.updatePrompt(it) }, Modifier.fillMaxWidth().heightIn(min = 96.dp).bringIntoViewOnFocus().testTag("prompt_field"),
                    label = { Text("Что сделать с фото?") }, minLines = 3, maxLines = 8, textStyle = LocalTextStyle.current.copy(fontSize = 16.sp))
                Button({ vm.openAi(); onOpenSite() }, Modifier.fillMaxWidth().height(56.dp).padding(top = 6.dp).testTag("btn_generate")) {
                    Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Открыть ${provider.title}", fontSize = 16.sp)
                }
            }
        } }
    }
}

@Composable
private fun Label(t: String) = Text(t, Modifier.background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp)
