package org.ivanivorontsov.fotomaster.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ivanivorontsov.fotomaster.io.Projects

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: EditorViewModel, onOpen: () -> Unit, onAccounts: () -> Unit) {
    val projects by vm.projects.collectAsState()
    val busy by vm.busy.collectAsState()
    val msg by vm.message.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.openUri(uri, onOpen) }
    var confirmDelete by remember { mutableStateOf<org.ivanivorontsov.fotomaster.io.ProjectInfo?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ФотоМастер", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAbout = true }) { Icon(Icons.Default.Info, "О программе") }
        }
        Text("Без рекламы, подписок и водяных знаков. Всё на телефоне.", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Открыть фото", fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAccounts, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Нейросети: Qwen, Алиса, ГигаЧат")
        }
        Spacer(Modifier.height(16.dp))
        if (projects.isNotEmpty()) Text("Недавние проекты (правки сохраняются автоматически)", color = Color.Gray, fontSize = 13.sp)
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { p ->
                val th = remember(p.id, p.modified) { Projects.thumb(p) }
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF22252B))
                    .combinedClickable(onClick = { vm.openProject(p, onOpen) }, onLongClick = { confirmDelete = p })) {
                    if (th != null) Image(th.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("Проект", Modifier.align(Alignment.Center))
                }
            }
        }
    }
    confirmDelete?.let { p ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Удалить проект?") }, text = { Text("Удаляется только копия в приложении, оригинал в галерее останется.") },
            confirmButton = { TextButton({ vm.deleteProject(p); confirmDelete = null }) { Text("Удалить") } }, dismissButton = { TextButton({ confirmDelete = null }) { Text("Отмена") } })
    }
    if (showAbout) AboutDialog { showAbout = false }
    BusyAndMessages(busy, msg) { vm.message.value = null }
}

@Composable
fun AboutDialog(onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClose) { Text("Закрыть") } },
        title = { Text("О программе") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                for ((h, t) in AboutTexts.sections) {
                    Text(h, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    Text(t, fontSize = 13.sp)
                }
            }
        })
}

object AboutTexts {
    val sections = listOf(
        "ФотоМастер" to "Тестовая версия. © 2026 Иван Воронцов. Все права защищены. Бесплатное использование для личных некоммерческих целей.",
        "Конфиденциальность" to "Фото и проекты хранятся только на телефоне. Приложение не собирает и не передаёт разработчику никаких данных: нет аналитики, рекламы и учётных записей. Нейросети открываются как обычный сайт во встроенном браузере: фото прикрепляете и отправляете вы сами, приложение ничего не нажимает, не вводит и не читает на сайте. Данные обрабатываются сервисом по его правилам. Вход в сервисы выполняется на их сайтах, приложение не видит паролей; cookies входа хранятся только во встроенном браузере на телефоне. Распознавание лиц и людей (Google ML Kit) работает на устройстве; библиотека передаёт в Google технические сведения о своей работе: модель устройства, версию системы, идентификатор установки и показатели скорости.",
        "Сторонние сервисы" to "Qwen — товарный знак Alibaba Group. Алиса и Яндекс — товарные знаки ООО «Яндекс». GigaChat (ГигаЧат) и Сбер — товарные знаки ПАО Сбербанк. ФотоМастер не связан с этими компаниями и не одобрен ими. Пользуясь сервисом, вы принимаете его собственные условия использования.",
        "Лицензии" to "Android Jetpack (AndroidX), Jetpack Compose, Material Components, Material Icons, Kotlin и kotlinx.coroutines — Apache License 2.0, © The Android Open Source Project, Google LLC, JetBrains s.r.o.\nJSON-java (org.json) — Public Domain.\nGoogle ML Kit (распознавание лиц, сегментация) — ML Kit Terms of Service, © Google LLC.\nТекст Apache License 2.0: apache.org/licenses/LICENSE-2.0"
    )
}

@Composable
fun BusyAndMessages(busy: String?, msg: String?, clear: () -> Unit) {
    if (busy != null) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(busy) } }
    }
    if (msg != null) {
        LaunchedEffect(msg) { kotlinx.coroutines.delay(4500); clear() }
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Snackbar(action = { TextButton(clear) { Text("OK") } }) { Text(msg) }
        }
    }
}
