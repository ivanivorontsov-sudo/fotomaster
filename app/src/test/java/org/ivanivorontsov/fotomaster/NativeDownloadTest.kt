package org.ivanivorontsov.fotomaster

import android.graphics.Bitmap
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.ivanivorontsov.fotomaster.ai.AiEngine
import org.ivanivorontsov.fotomaster.ai.NativeDownload
import org.ivanivorontsov.fotomaster.ai.Providers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
class NativeDownloadTest {
    private fun png(w: Int, h: Int): ByteArray { val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(0xFF3366CC.toInt()); return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }

    private class Srv(val body: ByteArray, val seen: MutableMap<String, String>) {
        val sock = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")); val port get() = sock.localPort
        init { Thread { while (!sock.isClosed) { val c = runCatching { sock.accept() }.getOrNull() ?: break; Thread { handle(c) }.start() } }.apply { isDaemon = true }.start() }
        private fun handle(c: java.net.Socket) = c.use {
            val r = c.getInputStream().bufferedReader(Charsets.ISO_8859_1); val path = r.readLine().split(" ")[1]
            while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break; val i = l.indexOf(':'); if (path.endsWith("/orig")) seen[l.substring(0, i).lowercase()] = l.substring(i + 1).trim() }
            val o = c.getOutputStream()
            when {
                path.endsWith("/orig") -> { o.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); o.write(body) }
                path == "/redir" -> o.write("HTTP/1.1 302 Found\r\nLocation: /get-shedevrum/1/abc/orig\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                else -> o.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
            o.flush()
        }
        fun stop() = sock.close()
    }
    private fun server(body: ByteArray, seen: MutableMap<String, String>) = Srv(body, seen)

    @Test fun headersAndRedirectsAndFallbackOrder() {
        val seen = ConcurrentHashMap<String, String>(); val body = png(40, 30); val s = server(body, seen)
        try {
            val base = "http://127.0.0.1:${s.port}"
            val h = NativeDownload.headers(NativeDownload.Request("$base/x", "sid=1", "UA/1", "https://alice.yandex.ru/"))
            assertEquals("sid=1", h["Cookie"]); assertEquals("UA/1", h["User-Agent"]); assertEquals("https://alice.yandex.ru/", h["Referer"])
            val r = NativeDownload.fetchFirst(listOf(NativeDownload.Request("$base/missing", null, "UA/1", null), NativeDownload.Request("$base/redir", "sid=1", "UA/1", "https://alice.yandex.ru/")))
            assertTrue(r.isSuccess); assertEquals(body.size, r.getOrThrow().size)
            assertEquals("sid=1", seen["cookie"]); assertEquals("UA/1", seen["user-agent"])
        } finally { s.stop() }
    }

    @Test fun userAcceptedImageIsImportedOnlyAfterConfirmation() {
        val seen = ConcurrentHashMap<String, String>(); val body = png(736, 1440); val s = server(body, seen)
        try {
            val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
            val base = "http://127.0.0.1:${s.port}"
            val engine = AiEngine(ctx); val wv = WebView(ctx); engine.attach(wv)
            CookieManager.getInstance().setCookie(base, "Session_id=abc")
            engine.open(Providers.load(ctx).first { it.id == "alice" })
            engine.offerImage.value = "$base/get-shedevrum/1/abc/orig"
            assertEquals(null, engine.imported.value)
            engine.acceptOffer()
            val end = System.currentTimeMillis() + 15000
            while (engine.imported.value == null && System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(50) }
            val im = engine.imported.value
            assertTrue(im != null)
            assertEquals(736, im!!.bitmap.width); assertEquals("Алиса AI", im.providerTitle)
            assertEquals("Session_id=abc", seen["cookie"])
            engine.offerImage.value = "$base/x"; engine.declineOffer(); assertEquals(null, engine.offerImage.value)
        } finally { s.stop() }
    }
}
