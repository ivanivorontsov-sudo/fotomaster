package org.ivanivorontsov.fotomaster.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.CookieManager
import org.ivanivorontsov.fotomaster.ai.AiState
import org.ivanivorontsov.fotomaster.ai.Providers
import org.ivanivorontsov.fotomaster.core.BgMode
import org.ivanivorontsov.fotomaster.io.BitmapIo

@Composable
fun AiPanel(vm: EditorViewModel, showWeb: Boolean, onToggleWeb: () -> Unit, onAccounts: () -> Unit) {
    val st by vm.ai.state.collectAsState()
    var prov by remember { mutableIntStateOf(0) }
    var prompt by remember { mutableStateOf(Providers.QUICK_PROMPTS[0]) }
    val providers = vm.providers
    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        ChipRow(providers.map { it.title }, prov) { prov = it }
        Text(providers[prov].returnsImage, fontSize = 11.sp, color = Color.Gray)
        when (val s = st) {
            is AiState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("${s.provider}: работаю в фоне…", Modifier.weight(1f)); TextButton({ vm.ai.cancel() }) { Text("Стоп") } }
                s.log.takeLast(6).forEach { Text(it, fontSize = 11.sp, color = Color.LightGray) }
            }
            is AiState.Done -> {
                Text("${s.provider}: готово ${s.bitmap.width}×${s.bitmap.height}", color = Accent)
                s.warnings.forEach { Text("⚠ $it", fontSize = 11.sp, color = Color(0xFFFFB74D)) }
                Image(s.bitmap.asImageBitmap(), null, Modifier.height(120.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button({ vm.addAiResult(s.bitmap, s.provider); vm.ai.state.value = AiState.Idle }) { Text("Добавить слоем") }
                    OutlinedButton({ vm.ai.state.value = AiState.Idle }) { Text("Отклонить") }
                }
            }
            is AiState.Failed -> {
                Text("${s.provider}: не получилось", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                Text(s.message, fontSize = 12.sp)
                s.log.takeLast(4).forEach { Text(it, fontSize = 10.sp, color = Color.Gray) }
                Text("Сделать на телефоне без интернета:", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton({ vm.autoEnhance(); vm.ai.state.value = AiState.Idle }) { Text("Автоулучшение") }
                    OutlinedButton({ vm.removeBackground(BgMode.TRANSPARENT); vm.ai.state.value = AiState.Idle }) { Text("Убрать фон") }
                }
                TextButton({ vm.ai.state.value = AiState.Idle }) { Text("Закрыть") }
            }
            AiState.Idle -> {
                FlowRowCompat { Providers.QUICK_PROMPTS.forEach { p -> AssistChip({ prompt = p }, { Text(p.take(28) + if (p.length > 28) "…" else "", fontSize = 11.sp) }) } }
                OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("Запрос нейросети") }, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ vm.renderForAi()?.let { vm.ai.run(providers[prov], it, prompt) } }) { Text("Отправить в ${providers[prov].title}") }
                    TextButton(onAccounts) { Text("Аккаунты") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(showWeb, { onToggleWeb() }); Spacer(Modifier.width(6.dp)); Text("Показать окно сайта (капча, проверка)", fontSize = 12.sp) }
        Text("Фото уходит на сервер выбранного сервиса через ваш вход на сайте. Пароли приложение не видит и не хранит.", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun AccountsScreen(vm: EditorViewModel, onBack: () -> Unit, onLogin: (String) -> Unit) {
    var checks by remember { mutableStateOf(mapOf<String, String>()) }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }; Text("Нейросети", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Text("Войдите один раз в свой аккаунт на сайте сервиса. Вход сохраняется в приложении (cookies WebView), ключи API не нужны. Логин и пароль вы вводите прямо на сайте, приложение их не читает и не хранит.", fontSize = 13.sp, color = Color.Gray)
        vm.providers.forEach { p ->
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(p.returnsImage, fontSize = 12.sp, color = Color.LightGray)
                    checks[p.id]?.let { Text(it, fontSize = 12.sp, color = Accent) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button({ onLogin(p.loginUrl) }) { Text("Войти") }
                        OutlinedButton({ checks = checks + (p.id to "Проверяю…"); vm.ai.onCheck = { ok, info -> checks = checks + (p.id to (if (ok) "✓ Вход выполнен" else "✗ Не вошли") + " — " + info.take(60)) }; vm.ai.check(p) }) { Text("Проверить") }
                        TextButton({ clearCookies(p.startUrl); checks = checks + (p.id to "Выход выполнен (cookies удалены)") }) { Text("Выйти") }
                    }
                }
            }
        }
    }
}

private fun clearCookies(url: String) {
    val cm = CookieManager.getInstance()
    val host = android.net.Uri.parse(url).host ?: return
    val base = host.split(".").takeLast(2).joinToString(".")
    listOf(url, "https://$base", "https://www.$base").forEach { u ->
        cm.getCookie(u)?.split(";")?.forEach { c -> val name = c.substringBefore("=").trim()
            listOf(host, ".$base", base).forEach { d -> cm.setCookie(u, "$name=; Max-Age=0; Domain=$d; Path=/") } }
    }
    cm.flush()
}

@Composable
fun LoginOverlay(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(8.dp), contentAlignment = Alignment.BottomCenter) {
        Button(onDone, Modifier.fillMaxWidth()) { Text("Готово — я вошёл") }
    }
}

@Composable
fun WebOverlayBar(onHide: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(8.dp), contentAlignment = Alignment.TopEnd) {
        FilledTonalButton(onHide) { Text("Скрыть окно сайта") }
    }
}

@Composable
fun ExportDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    var fmt by remember { mutableIntStateOf(0) }
    var quality by remember { mutableFloatStateOf(95f) }
    var size by remember { mutableIntStateOf(0) }
    var exif by remember { mutableStateOf(true) }
    var gps by remember { mutableStateOf(true) }
    val sizes = listOf(0 to "Оригинал ${vm.fullW}×${vm.fullH}", 4096 to "4096", 2048 to "2048", 1080 to "1080")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Сохранить копию") }, text = {
        Column {
            ChipRow(BitmapIo.Format.entries.map { it.name }, fmt) { fmt = it }
            if (fmt != 1) FSlider("Качество", quality, 70f..100f, 95f, { quality = it }, {}) { it.toInt().toString() }
            ChipRow(sizes.map { it.second }, size) { size = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(exif, { exif = it }); Text("Сохранить EXIF (камера, дата, настройки)") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(gps, { gps = it }, enabled = exif); Text("Сохранить геолокацию") }
            Text("Без водяных знаков. Оригинал не перезаписывается.", fontSize = 11.sp, color = Color.Gray)
        }
    }, confirmButton = { Button({ onDismiss(); vm.export(ExportOptions(BitmapIo.Format.entries[fmt], quality.toInt(), sizes[size].first, exif, gps)) { vm.message.value = it } }) { Text("Сохранить") } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } })
}
