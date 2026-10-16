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
import org.ivanivorontsov.fotomaster.ai.Providers
import org.ivanivorontsov.fotomaster.core.BgMode
import org.ivanivorontsov.fotomaster.io.BitmapIo

@Composable
fun AccountsScreen(vm: EditorViewModel, onBack: () -> Unit, onOpen: (org.ivanivorontsov.fotomaster.ai.AiProvider) -> Unit) {
    var notes by remember { mutableStateOf(mapOf<String, String>()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }; Text("Нейросети", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Text("ФотоМастер просто открывает сайт сервиса во встроенном браузере. Входите, прикрепляйте фото и нажимайте «Отправить» вы сами — приложение ничего не делает на сайте за вас и не читает страницу. Пароли вводятся прямо на сайте, приложение их не видит.", fontSize = 13.sp, color = Color.Gray)
        vm.providers.forEach { p ->
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(p.hint, fontSize = 12.sp, color = Color.LightGray)
                    notes[p.id]?.let { Text(it, fontSize = 12.sp, color = Accent) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button({ onOpen(p) }) { Text("Открыть сайт") }
                        if (p.terms.isNotBlank()) TextButton({ runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.terms))) } }) { Text("Условия") }
                        TextButton({ clearCookies(p.startUrl); notes = notes + (p.id to "Выход выполнен (cookies удалены)") }) { Text("Выйти") }
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
fun SiteBar(provider: org.ivanivorontsov.fotomaster.ai.AiProvider?, onClose: () -> Unit) {
    var showHint by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(8.dp)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (showHint && provider != null) Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Запрос скопирован. ${provider.hint} Готовую картинку долго нажмите и выберите «Взять в ФотоМастер».", fontSize = 13.sp)
                    TextButton({ showHint = false }) { Text("Понятно") }
                }
            }
            FilledTonalButton(onClose) { Text("Закрыть сайт") }
        }
    }
}

@Composable
fun OfferDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(onDismissRequest = onDecline, title = { Text("Взять в ФотоМастер?") },
        text = { Text("Эта картинка будет добавлена во вкладку «ИИ-промпт» рядом с оригиналом.") },
        confirmButton = { Button(onAccept) { Text("Взять в ФотоМастер") } },
        dismissButton = { TextButton(onDecline) { Text("Отмена") } })
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
