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
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow

data class Imported(val providerTitle: String, val bitmap: Bitmap)

class AiEngine(private val ctx: Context) {
    var webView: WebView? = null; private set
    private val main = Handler(Looper.getMainLooper())
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    var provider: AiProvider? = null; private set
    val pickRequest = MutableStateFlow(0)
    val offerImage = MutableStateFlow<String?>(null)
    val imported = MutableStateFlow<Imported?>(null)
    val status = MutableStateFlow<String?>(null)

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
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) { CookieManager.getInstance().flush() }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb; pickRequest.value++
                return true
            }
        }
        view.setOnLongClickListener {
            val h = view.hitTestResult
            val url = h.extra
            if ((h.type == WebView.HitTestResult.IMAGE_TYPE || h.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && url != null) { offerImage.value = url; true } else false
        }
        view.setDownloadListener { url, _, _, mime, _ ->
            if (mime.isNullOrBlank() || mime.startsWith("image/") || url.startsWith("data:image")) offerImage.value = url
            else status.value = "Это не картинка: $mime"
        }
    }

    fun deliverPicked(uri: Uri?) {
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }

    fun open(p: AiProvider) {
        provider = p
        val v = webView ?: return
        val cur = v.url
        if (cur == null || Uri.parse(cur).host != Uri.parse(p.startUrl).host) v.loadUrl(p.startUrl)
    }

    fun close() { deliverPicked(null) }

    fun declineOffer() { offerImage.value = null }

    fun acceptOffer() {
        val url = offerImage.value ?: return
        offerImage.value = null
        val title = provider?.title ?: "ИИ"
        if (url.startsWith("data:")) {
            val bytes = runCatching { Base64.decode(url.substringAfter(","), Base64.DEFAULT) }.getOrNull()
            deliverBytes(title, bytes); return
        }
        if (!url.startsWith("http")) { status.value = "Эту картинку нельзя взять напрямую. Сохраните её в галерею и нажмите «Загрузить результат из галереи»."; return }
        val req = NativeDownload.Request(url, CookieManager.getInstance().getCookie(url), webView?.settings?.userAgentString ?: "", webView?.url?.takeIf { it.startsWith("http") })
        status.value = "Загружаю картинку…"
        Thread {
            val r = NativeDownload.fetchFirst(listOf(req))
            main.post { r.fold({ deliverBytes(title, it) }, { status.value = "Не удалось загрузить картинку: ${it.message}. Сохраните её в галерею и загрузите оттуда." }) }
        }.start()
    }

    private fun deliverBytes(title: String, bytes: ByteArray?) {
        val bm = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bm == null) { status.value = "Файл не является картинкой"; return }
        status.value = null
        imported.value = Imported(title, bm)
    }

    fun importBitmap(bm: Bitmap) { imported.value = Imported(provider?.title ?: "ИИ", bm) }
}
