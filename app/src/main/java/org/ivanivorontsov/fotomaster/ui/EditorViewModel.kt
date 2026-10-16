package org.ivanivorontsov.fotomaster.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ivanivorontsov.fotomaster.ai.AiEngine
import org.ivanivorontsov.fotomaster.ai.Providers
import org.ivanivorontsov.fotomaster.core.Adjustments
import org.ivanivorontsov.fotomaster.core.AutoEnhance
import org.ivanivorontsov.fotomaster.core.Doc
import org.ivanivorontsov.fotomaster.core.GeometryOps
import org.ivanivorontsov.fotomaster.core.History
import org.ivanivorontsov.fotomaster.core.Img
import org.ivanivorontsov.fotomaster.core.Layer
import org.ivanivorontsov.fotomaster.core.Preset
import org.ivanivorontsov.fotomaster.core.Presets
import org.ivanivorontsov.fotomaster.core.Renderer
import org.ivanivorontsov.fotomaster.io.BitmapIo
import org.ivanivorontsov.fotomaster.io.OnDeviceAi
import org.ivanivorontsov.fotomaster.io.ProjectAssets
import org.ivanivorontsov.fotomaster.io.ProjectInfo
import org.ivanivorontsov.fotomaster.io.Projects
import java.io.File

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    val ctx get() = getApplication<Application>()
    val ai = AiEngine(app)
    val providers by lazy { Providers.load(app) }

    var project: ProjectInfo? = null; private set
    var assets: ProjectAssets? = null; private set
    var source: Img? = null; private set
    private var proxy: Img? = null
    var fullW = 0; private set
    var fullH = 0; private set
    private var history = History()

    val doc = MutableStateFlow(Doc())
    val rendered = MutableStateFlow<Bitmap?>(null)
    val before = MutableStateFlow<Bitmap?>(null)
    val busy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)
    val historyEntries = MutableStateFlow<List<String>>(emptyList())
    val historyIndex = MutableStateFlow(0)
    val renderNoCrop = MutableStateFlow(false)
    val selectedLayer = MutableStateFlow<Long?>(null)
    val userPresets = MutableStateFlow<List<Preset>>(emptyList())
    val projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    val pendingOpen = MutableStateFlow<Uri?>(null)
    private var renderJob: Job? = null

    private val prefs = app.getSharedPreferences("fotomaster", android.content.Context.MODE_PRIVATE)
    val prompt by lazy { org.ivanivorontsov.fotomaster.ai.PromptSession<Bitmap>(object : org.ivanivorontsov.fotomaster.ai.KeyValue {
        override fun get(key: String) = prefs.getString(key, null)
        override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    }, providers.map { it.id }) }
    val promptTick = MutableStateFlow(0)
    private val promptBefore = HashMap<Int, Bitmap>()
    private var pendingBefore: Bitmap? = null
    private fun bump() { promptTick.value++ }

    init {
        refreshProjects()
        viewModelScope.launch {
            ai.imported.collect { im ->
                if (im == null) return@collect
                ai.imported.value = null
                val r = prompt.addResult(im.providerTitle, im.bitmap, im.bitmap.width, im.bitmap.height)
                pendingBefore?.let { promptBefore[r.id] = it }
                message.value = "Картинка получена из ${im.providerTitle}"
                bump()
            }
        }
    }

    fun promptBeforeOf(id: Int): Bitmap? = promptBefore[id]
    fun putPromptBefore(id: Int, b: Bitmap) { promptBefore[id] = b; bump() }

    fun selectProvider(id: String) { prompt.selectProvider(id); prompt.onProviderChanged(); bump() }
    fun dismissAiError() { prompt.dismissError(); bump() }

    fun copyPrompt(): Boolean {
        val t = prompt.prompt.trim(); if (t.isEmpty()) return false
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Запрос", t))
        return true
    }

    fun openAi() {
        val req = prompt.beginRequest()
        val p = providers.firstOrNull { it.id == req.first } ?: return
        pendingBefore = renderForAi()
        copyPrompt()
        ai.open(p); bump()
    }

    fun savePhotoForAi() = viewModelScope.launch {
        val bm = renderForAi() ?: return@launch
        busy.value = "Сохраняю фото для отправки…"
        try {
            withContext(Dispatchers.IO) { BitmapIo.save(ctx, bm, null, BitmapIo.Format.JPEG, 93, keepExif = false, keepGps = false) }
            message.value = "Фото сохранено в Pictures/FotoMaster. Выберите его на сайте нейросети."
        } catch (e: Throwable) { message.value = "Не удалось сохранить: ${e.message}" } finally { busy.value = null }
    }

    fun importResult(uri: Uri) = viewModelScope.launch {
        try {
            val bm = withContext(Dispatchers.IO) { BitmapIo.decode(ctx, uri, 4096) }
            if (pendingBefore == null) pendingBefore = renderForAi()
            if (prompt.lastRequest == null) prompt.startRequest()
            ai.importBitmap(bm)
        } catch (e: Throwable) { message.value = "Не удалось открыть картинку: ${e.message}" }
    }

    fun applyPromptResult(id: Int) {
        val r = prompt.results.firstOrNull { it.id == id } ?: return
        addAiResult(r.image, r.providerTitle); prompt.markApplied(id); bump()
    }

    fun savePromptResult(id: Int) = viewModelScope.launch {
        val r = prompt.results.firstOrNull { it.id == id } ?: return@launch
        busy.value = "Сохраняю результат…"
        try {
            withContext(Dispatchers.IO) { BitmapIo.save(ctx, r.image, project?.let { Uri.fromFile(it.source) }, BitmapIo.Format.JPEG, 95, keepExif = true, keepGps = true) }
            prompt.markSaved(id); bump()
            message.value = "Сохранено в Pictures/FotoMaster как есть (${r.width}×${r.height})"
        } catch (e: Throwable) { message.value = "Не удалось сохранить: ${e.message}" } finally { busy.value = null }
    }

    fun refreshProjects() { viewModelScope.launch(Dispatchers.IO) { projects.value = Projects.list(ctx) } }

    fun openUri(uri: Uri, onReady: () -> Unit) = viewModelScope.launch {
        busy.value = "Открываю фото…"
        try {
            val p = withContext(Dispatchers.IO) { Projects.create(ctx, uri) }
            load(p); onReady()
        } catch (e: Throwable) { message.value = "Не удалось открыть: ${e.message}" } finally { busy.value = null }
    }

    fun openProject(p: ProjectInfo, onReady: () -> Unit) = viewModelScope.launch {
        busy.value = "Открываю проект…"
        try { load(p); onReady() } catch (e: Throwable) { message.value = "Ошибка: ${e.message}" } finally { busy.value = null }
    }

    fun deleteProject(p: ProjectInfo) { viewModelScope.launch(Dispatchers.IO) { Projects.delete(p); refreshProjects() } }

    private suspend fun load(p: ProjectInfo) {
        project = p; assets = ProjectAssets(ctx, p.id)
        prompt.resetForProject(p.id); promptBefore.clear(); bump()
        val uri = Uri.fromFile(p.source)
        val (w, h) = withContext(Dispatchers.IO) { BitmapIo.bounds(ctx, uri) }
        fullW = w; fullH = h
        val bm = withContext(Dispatchers.IO) { BitmapIo.decode(ctx, uri, PREVIEW) }
        source = withContext(Dispatchers.Default) { BitmapIo.toImg(bm) }
        proxy = source?.let { s -> if (maxOf(s.w, s.h) > 900) { val (pw, ph) = Renderer.previewSize(s.w, s.h, 720); withContext(Dispatchers.Default) { org.ivanivorontsov.fotomaster.core.resize(s, pw, ph) } } else null }
        val d = withContext(Dispatchers.IO) { Projects.loadDoc(p) }
        history = History(d)
        userPresets.value = Projects.userPresets(ctx)
        syncHistory(); doc.value = d
        startRendering()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun startRendering() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(doc, renderNoCrop) { d, nc -> d to nc }.conflate().collectLatest { (d, nc) ->
                val src = source ?: return@collectLatest
                val a = assets ?: return@collectLatest
                val small = proxy
                if (small != null) {
                    val quick = withContext(Dispatchers.Default) { Renderer.render(small, d, a, withCrop = !nc, scale = small.w.toFloat() / maxOf(1, fullW)) }
                    rendered.value = withContext(Dispatchers.Default) { BitmapIo.toBitmap(quick) }
                    kotlinx.coroutines.delay(120)
                }
                val img = withContext(Dispatchers.Default) { Renderer.render(src, d, a, withCrop = !nc, scale = src.w.toFloat() / maxOf(1, fullW)) }
                val bm = withContext(Dispatchers.Default) { BitmapIo.toBitmap(img) }
                rendered.value = bm
                before.value = withContext(Dispatchers.Default) { BitmapIo.toBitmap(GeometryOps.apply(src, d.geometry, !nc)) }
            }
        }
    }

    private fun syncHistory() {
        canUndo.value = history.canUndo; canRedo.value = history.canRedo
        historyEntries.value = history.entries; historyIndex.value = history.index
    }

    private fun autosave() {
        val p = project ?: return; val d = history.current; val bm = rendered.value
        viewModelScope.launch(Dispatchers.IO) {
            val th = bm?.let { val s = 320f / maxOf(it.width, it.height); Bitmap.createScaledBitmap(it, maxOf(1, (it.width * s).toInt()), maxOf(1, (it.height * s).toInt()), true) }
            Projects.saveDoc(p, d, th)
        }
    }

    fun preview(d: Doc) { doc.value = d }
    fun commit(label: String, d: Doc = doc.value) { history.push(label, d); doc.value = d; syncHistory(); autosave() }
    fun cancelPreview() { doc.value = history.current }
    fun undo() { doc.value = history.undo(); syncHistory(); autosave() }
    fun redo() { doc.value = history.redo(); syncHistory(); autosave() }
    fun jump(i: Int) { doc.value = history.jump(i); syncHistory(); autosave() }

    fun setAdj(a: Adjustments) = preview(doc.value.copy(adj = a))

    fun autoEnhance() = viewModelScope.launch {
        val src = source ?: return@launch
        busy.value = "Анализирую фото…"
        val faces = withContext(Dispatchers.Default) { OnDeviceAi.faceCount(BitmapIo.toBitmap(src)) }
        val adj = withContext(Dispatchers.Default) { AutoEnhance.suggest(src, faces > 0) }
        busy.value = null
        commit("Авто" + if (faces > 0) " (портрет)" else "", doc.value.copy(adj = adj.copy(hsl = doc.value.adj.hsl, curve = doc.value.adj.curve)))
        message.value = if (faces > 0) "Автоулучшение: найдено лиц: $faces, кожа не пересушена" else "Автоулучшение применено, его можно подправить ползунками"
    }

    fun applyPreset(p: Preset) = commit("Пресет «${p.name}»", doc.value.copy(adj = p.adj))
    fun savePreset(name: String) { Projects.addPreset(ctx, name, doc.value.adj); userPresets.value = Projects.userPresets(ctx); message.value = "Пресет «$name» сохранён" }
    fun deletePreset(p: Preset) { Projects.saveUserPresets(ctx, userPresets.value.filter { it.name != p.name }); userPresets.value = Projects.userPresets(ctx) }

    fun addLayer(l: Layer, label: String) { commit(label, doc.value.copy(layers = doc.value.layers + l)); selectedLayer.value = l.id }
    fun updateLayer(l: Layer, label: String? = null) { val d = doc.value.replaceLayer(l); if (label != null) commit(label, d) else preview(d) }
    fun removeLayer(id: Long) { commit("Удалён слой", doc.value.copy(layers = doc.value.layers.filter { it.id != id })); if (selectedLayer.value == id) selectedLayer.value = null }
    fun moveLayer(id: Long, dir: Int) {
        val l = doc.value.layers.toMutableList(); val i = l.indexOfFirst { it.id == id }; val j = i + dir
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        commit("Порядок слоёв", doc.value.copy(layers = l))
    }

    fun removeBackground(mode: org.ivanivorontsov.fotomaster.core.BgMode = org.ivanivorontsov.fotomaster.core.BgMode.TRANSPARENT) = viewModelScope.launch {
        val src = source ?: return@launch; val a = assets ?: return@launch
        busy.value = "Отделяю человека от фона (на телефоне)…"
        try {
            val ref = "mask_" + System.currentTimeMillis()
            withContext(Dispatchers.Default) {
                val m = OnDeviceAi.personMask(BitmapIo.toBitmap(src))
                a.saveMask(ref, m.data, m.w, m.h)
            }
            addLayer(Layer.Cutout(doc.value.nextId(), maskRef = ref, mode = mode), "Удаление фона")
            message.value = "Фон отделён. Подправьте края кистью «Добавить/Убрать»"
        } catch (e: Throwable) { message.value = "Не удалось отделить фон: ${e.message}" } finally { busy.value = null }
    }

    fun personMaskLayer() = viewModelScope.launch {
        val src = source ?: return@launch; val a = assets ?: return@launch
        busy.value = "Ищу человека…"
        try {
            val ref = "mask_" + System.currentTimeMillis()
            withContext(Dispatchers.Default) { val m = OnDeviceAi.personMask(BitmapIo.toBitmap(src)); a.saveMask(ref, m.data, m.w, m.h) }
            addLayer(Layer.Local(doc.value.nextId(), name = "Маска: человек", shape = org.ivanivorontsov.fotomaster.core.MaskShape.PERSON, maskRef = ref), "Маска «Человек»")
        } catch (e: Throwable) { message.value = "Ошибка маски: ${e.message}" } finally { busy.value = null }
    }

    fun renderForAi(): Bitmap? {
        val src = source ?: return null; val a = assets ?: return null
        return BitmapIo.toBitmap(Renderer.render(src, doc.value, a, withGeometry = false))
    }

    fun addAiResult(bm: Bitmap, providerTitle: String) {
        val a = assets ?: return
        val ref = "ai_" + System.currentTimeMillis()
        a.saveImage(ref, bm)
        addLayer(Layer.Picture(doc.value.nextId(), name = "ИИ: $providerTitle", imageRef = ref), "Результат $providerTitle")
        message.value = "Результат добавлен слоем: можно менять прозрачность и стирать части"
    }

    fun export(opts: ExportOptions, done: (String) -> Unit) = viewModelScope.launch {
        val p = project ?: return@launch; val a = assets ?: return@launch
        busy.value = "Экспорт в полном размере…"
        try {
            val uri = withContext(Dispatchers.Default) {
                val srcUri = Uri.fromFile(p.source)
                var maxSide = opts.maxSide
                var result: Bitmap? = null
                var attempt = 0
                while (result == null) {
                    try {
                        val bm = BitmapIo.decode(ctx, srcUri, maxSide)
                        val img = BitmapIo.toImg(bm); bm.recycle()
                        val out = Renderer.render(img, history.current, a, scale = img.w.toFloat() / maxOf(1, fullW))
                        result = BitmapIo.toBitmap(out)
                    } catch (oom: OutOfMemoryError) {
                        attempt++; System.gc()
                        maxSide = ((if (maxSide == 0) maxOf(fullW, fullH) else maxSide) * 0.8f).toInt()
                        withContext(Dispatchers.Main) { busy.value = "Мало памяти, уменьшаю до $maxSide px…" }
                        if (attempt > 4) throw oom
                    }
                }
                val r = result!!
                val u = BitmapIo.save(ctx, r, srcUri, opts.format, opts.quality, opts.keepExif, opts.keepGps)
                "${r.width}×${r.height}" to u
            }
            done("Сохранено в Галерею → Pictures/FotoMaster (${uri.first}). Оригинал не тронут.")
        } catch (e: Throwable) { message.value = "Ошибка экспорта: ${e.message}" } finally { busy.value = null }
    }

    companion object { const val PREVIEW = 1600 }
}

data class ExportOptions(val format: BitmapIo.Format = BitmapIo.Format.JPEG, val quality: Int = 95, val maxSide: Int = 0, val keepExif: Boolean = true, val keepGps: Boolean = true)
