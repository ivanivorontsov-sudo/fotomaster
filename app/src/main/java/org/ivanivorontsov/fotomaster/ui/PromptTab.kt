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
import org.ivanivorontsov.fotomaster.ai.AiState
import org.ivanivorontsov.fotomaster.ai.LoginState
import org.ivanivorontsov.fotomaster.ai.PromptSession
import org.ivanivorontsov.fotomaster.core.BgMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptTab(vm: EditorViewModel, onLogin: (String) -> Unit, showWeb: Boolean, onToggleWeb: () -> Unit) {
    val tick by vm.promptTick.collectAsState()
    val st by vm.ai.state.collectAsState()
    val current by vm.rendered.collectAsState()
    val s = vm.prompt
    var text by remember(tick == 0) { mutableStateOf(s.prompt) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var holdBefore by remember { mutableStateOf(false) }
    val vt = remember { ViewTransform() }
    val provider = vm.providers.first { it.id == s.providerId }
    val selected = remember(tick) { s.selected }
    val running = st is AiState.Running
    androidx.compose.runtime.LaunchedEffect(provider.id) { vm.ai.prewarm(provider) }
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
                Text(if (running) "" else "Результат появится здесь, рядом с оригиналом", Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Color(0xAA000000)).padding(6.dp), fontSize = 12.sp)
            }
            if (running) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("${(st as AiState.Running).provider}: генерирую…", Modifier.padding(top = 8.dp))
                    Text((st as AiState.Running).log.lastOrNull() ?: "", fontSize = 11.sp, color = Color.LightGray, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface) { Column {
            Column(Modifier.heightIn(max = if (androidx.compose.foundation.layout.WindowInsets.isImeVisible) 90.dp else 230.dp).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (selected != null && !running) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button({ vm.applyPromptResult(selected.id) }, Modifier.weight(1f).testTag("btn_apply"), enabled = !selected.applied, contentPadding = PaddingValues(4.dp)) { Text(if (selected.applied) "Применено" else "Применить", fontSize = 13.sp) }
                    OutlinedButton({ vm.generate(retry = true) }, Modifier.weight(1f).testTag("btn_retry"), contentPadding = PaddingValues(4.dp)) { Text("Ещё раз", fontSize = 13.sp) }
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
                when (s.loginState()) {
                    LoginState.LOGGED_OUT -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Color(0x33FFB74D), RoundedCornerShape(8.dp)).padding(6.dp)) {
                        Icon(Icons.Default.Login, null, tint = Color(0xFFFFB74D)); Spacer(Modifier.width(6.dp))
                        Text("Вход в ${provider.title} не выполнен", Modifier.weight(1f), fontSize = 13.sp)
                        Button({ onLogin(provider.loginUrl) }, Modifier.testTag("btn_login")) { Text("Войти") }
                    }
                    LoginState.CHECKING -> Text("Проверяю вход в ${provider.title}…", fontSize = 12.sp, color = Color.Gray)
                    LoginState.LOGGED_IN -> Text("✓ Вход в ${provider.title} выполнен", fontSize = 12.sp, color = Accent)
                    LoginState.UNKNOWN -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Вход в ${provider.title} не проверен", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        TextButton({ vm.checkLogin() }, enabled = !running) { Text("Проверить") }
                        TextButton({ onLogin(provider.loginUrl) }, Modifier.testTag("btn_login")) { Text("Войти") }
                    }
                }
                Text(provider.returnsImage, fontSize = 11.sp, color = Color.Gray)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("prompt_chips")) {
                    PromptSession.QUICK.forEachIndexed { i, (title, full) ->
                        SuggestionChip({ s.applyChip(i); text = full; vm.promptTick.value++ }, { Text(title, fontSize = 12.sp) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(showWeb, { onToggleWeb() }); Spacer(Modifier.width(6.dp)); Text("Показать окно сайта (капча, проверка)", fontSize = 12.sp) }
                val dump by vm.ai.lastDump.collectAsState()
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ vm.ai.dumpPage(provider) }, enabled = !running, modifier = Modifier.testTag("btn_dump")) { Text("Сохранить снимок страницы для отладки", fontSize = 12.sp) }
                    dump?.let { f -> TextButton({ shareFile(ctx, f) }) { Text("Отправить", fontSize = 12.sp) } }
                }
                Text("Фото отправляется выбранному сервису через ваш вход на его сайте. Пароли приложение не хранит.", fontSize = 10.sp, color = Color.Gray)
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
                                TextButton({ vm.generate(retry = true) }, Modifier.testTag("btn_error_retry")) { Text("Ещё раз") }
                                if (f.log.isNotEmpty()) TextButton({ showLog = !showLog }, Modifier.testTag("btn_log")) { Text(if (showLog) "Скрыть журнал" else "Журнал (${f.log.size})") }
                                TextButton({ vm.dismissAiError(); vm.autoEnhance() }) { Text("Автоулучшение") }
                            }
                            if (showLog) Text(f.log.takeLast(40).joinToString("\n"), Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()).testTag("ai_log"), fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                }
                OutlinedTextField(text, { text = it; s.updatePrompt(it) }, Modifier.fillMaxWidth().heightIn(min = 96.dp).bringIntoViewOnFocus().testTag("prompt_field"),
                    label = { Text("Что сделать с фото?") }, minLines = 3, maxLines = 8, textStyle = LocalTextStyle.current.copy(fontSize = 16.sp))
                Button({ if (running) vm.ai.cancel() else vm.generate() }, Modifier.fillMaxWidth().height(56.dp).padding(top = 6.dp).testTag("btn_generate"), enabled = running || text.isNotBlank(),
                    colors = if (running) ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)) else ButtonDefaults.buttonColors()) {
                    if (running) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Остановить") }
                    else { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Сгенерировать в ${provider.title}", fontSize = 16.sp) }
                }
            }
        } }
    }
}

@Composable
private fun Label(t: String) = Text(t, Modifier.background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp)

fun shareFile(ctx: android.content.Context, f: java.io.File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json").putExtra(android.content.Intent.EXTRA_STREAM, uri).putExtra(android.content.Intent.EXTRA_SUBJECT, f.name).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(android.content.Intent.createChooser(send, "Снимок страницы").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}
