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
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(8.dp), contentAlignment = Alignment.BottomCenter) {
        Button(onDone, Modifier.fillMaxWidth()) { Text("Готово — я вошёл") }
    }
}

@Composable
fun WebOverlayBar(needUser: String?, onHide: () -> Unit, onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(8.dp)) {
        if (needUser != null) Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(needUser, fontSize = 14.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onContinue, Modifier.weight(1f)) { Text("Продолжить") }
                    OutlinedButton(onHide) { Text("Отмена") }
                }
            }
        } else FilledTonalButton(onHide, Modifier.align(Alignment.TopEnd)) { Text("Скрыть окно сайта") }
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
