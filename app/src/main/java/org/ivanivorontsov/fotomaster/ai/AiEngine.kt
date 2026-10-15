package org.ivanivorontsov.fotomaster.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class AiState {
    data object Idle : AiState()
    data class Running(val provider: String, val log: List<String>) : AiState()
    data class Done(val provider: String, val bitmap: Bitmap, val log: List<String>, val warnings: List<String>) : AiState()
    data class Failed(val provider: String, val code: String, val message: String, val log: List<String>) : AiState()
}

class AiEngine(private val ctx: Context) {
    val state = MutableStateFlow<AiState>(AiState.Idle)
    var webView: WebView? = null; private set
    private val main = Handler(Looper.getMainLooper())
    private var provider: AiProvider? = null
    private var prompt = ""
    private var imageB64 = ""
    private var imageFile: File? = null
    private var mode = "run"
    private val log = mutableListOf<String>()
    private val warnings = mutableListOf<String>()
    private var chooserArmed = false
    @Volatile private var chooserUsed = false
    private var injected = false
    private val chunks = StringBuilder()
    private var timeoutTask: Runnable? = null
    var onCheck: ((Boolean, String) -> Unit)? = null

    companion object {
        fun mobileUa(view: WebView): String =
            WebSettings.getDefaultUserAgent(view.context).replace("; wv)", ")").replace(Regex("Version/\\d+\\.\\d+ "), "")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView) {
        webView = view
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            loadWithOverviewMode = true; useWideViewPort = true; mediaPlaybackRequiresUserGesture = true
            userAgentString = mobileUa(view); allowFileAccess = false
        }
        view.addJavascriptInterface(Bridge(), "FMBridge")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) { if (provider != null && !injected) { injected = true; main.postDelayed({ inject() }, 1500) } }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                val f = imageFile
                if (chooserArmed && f != null) {
                    chooserArmed = false; chooserUsed = true
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    cb.onReceiveValue(arrayOf(uri)); add("окно выбора файла перехвачено, фото подставлено"); return true
                }
                cb.onReceiveValue(null); return true
            }
        }
    }

    private fun add(s: String) { log.add(s); if (log.size > 200) log.removeAt(0)
        val p = provider ?: return; if (state.value is AiState.Running) state.value = AiState.Running(p.title, log.toList()) }

    private fun inject() {
        val js = ctx.assets.open("ai/runner.js").bufferedReader().readText()
        webView?.evaluateJavascript(js, null)
    }

    fun run(p: AiProvider, photo: Bitmap, userPrompt: String, timeoutMs: Long = 330_000) {
        val v = webView ?: return
        provider = p; prompt = userPrompt; mode = "run"; injected = false; chooserUsed = false; chooserArmed = false
        log.clear(); warnings.clear(); chunks.clear()
        val scaled = limit(photo, 2560)
        val bos = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 93, bos)
        val bytes = bos.toByteArray()
        imageB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        imageFile = File(ctx.cacheDir, "ai_upload/photo.jpg").apply { parentFile?.mkdirs(); writeBytes(bytes) }
        state.value = AiState.Running(p.title, listOf("Открываю ${p.title}…"))
        timeoutTask?.let { main.removeCallbacks(it) }
        timeoutTask = Runnable { if (state.value is AiState.Running) finishFail("timeout", "Сервис не ответил вовремя") }.also { main.postDelayed(it, timeoutMs) }
        v.stopLoading(); v.loadUrl(p.startUrl)
    }

    fun check(p: AiProvider) {
        val v = webView ?: return
        provider = p; mode = "check"; injected = false
        v.loadUrl(p.startUrl)
    }

    fun cancel() { webView?.stopLoading(); webView?.loadUrl("about:blank"); provider = null; state.value = AiState.Idle }

    private fun limit(b: Bitmap, max: Int): Bitmap {
        val m = maxOf(b.width, b.height); if (m <= max) return b
        val s = max.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }

    private fun finishOk(bytes: ByteArray) {
        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bm == null) { finishFail("decode", "Скачанный файл не является картинкой"); return }
        timeoutTask?.let { main.removeCallbacks(it) }
        state.value = AiState.Done(provider?.title ?: "", bm, log.toList(), warnings.toList())
    }

    private fun finishFail(code: String, msg: String) {
        timeoutTask?.let { main.removeCallbacks(it) }
        state.value = AiState.Failed(provider?.title ?: "", code, msg, log.toList())
    }

    private fun download(url: String) {
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
                c.setRequestProperty("User-Agent", webView?.settings?.userAgentString ?: "")
                provider?.let { c.setRequestProperty("Referer", it.startUrl) }
                c.connectTimeout = 30000; c.readTimeout = 60000
                val bytes = c.inputStream.use { it.readBytes() }
                main.post { finishOk(bytes) }
            } catch (e: Exception) { main.post { finishFail("download", "Не удалось скачать результат: ${e.message}") } }
        }.start()
    }

    inner class Bridge {
        @JavascriptInterface fun config(): String = provider?.raw?.toString() ?: "{}"
        @JavascriptInterface fun prompt(): String = prompt
        @JavascriptInterface fun mode(): String = mode
        @JavascriptInterface fun imageBase64(): String = imageB64
        @JavascriptInterface fun armChooser() { chooserArmed = true; chooserUsed = false }
        @JavascriptInterface fun chooserUsed(): Boolean = chooserUsed
        @JavascriptInterface fun log(s: String) { main.post { add(s) } }
        @JavascriptInterface fun warn(s: String) { main.post { warnings.add(s); add("⚠ $s") } }
        @JavascriptInterface fun fail(code: String, msg: String) { main.post { finishFail(code, msg) } }
        @JavascriptInterface fun resultUrl(url: String) { main.post { add("скачиваю результат приложением"); download(url) } }
        @JavascriptInterface fun resultBegin(url: String, len: Int) { synchronized(chunks) { chunks.setLength(0) } }
        @JavascriptInterface fun resultChunk(s: String) { synchronized(chunks) { chunks.append(s) } }
        @JavascriptInterface fun resultEnd() { val b = synchronized(chunks) { chunks.toString() }; val bytes = Base64.decode(b, Base64.DEFAULT); main.post { finishOk(bytes) } }
        @JavascriptInterface fun checkDone(ok: Boolean, info: String) { main.post { onCheck?.invoke(ok, info) } }
    }
}
