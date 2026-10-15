package org.ivanivorontsov.fotomaster.ai

import java.net.HttpURLConnection
import java.net.URL

object NativeDownload {
    data class Request(val url: String, val cookie: String?, val userAgent: String, val referer: String?)

    fun headers(r: Request): Map<String, String> = buildMap {
        r.cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        if (r.userAgent.isNotBlank()) put("User-Agent", r.userAgent)
        r.referer?.let { put("Referer", it) }
        put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
    }

    fun fetch(r: Request): ByteArray {
        var url = r.url
        repeat(5) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false; c.connectTimeout = 30000; c.readTimeout = 60000
            headers(r).forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code in 300..399) { url = URL(URL(url), c.getHeaderField("Location") ?: error("redirect без адреса")).toString(); c.disconnect(); return@repeat }
            if (code !in 200..299) error("HTTP $code")
            return c.inputStream.use { it.readBytes() }
        }
        error("слишком много перенаправлений")
    }

    fun fetchFirst(list: List<Request>): Result<ByteArray> {
        var last: Throwable = IllegalStateException("нет адресов")
        for (r in list) { val x = runCatching { fetch(r) }; if (x.isSuccess) return x; last = x.exceptionOrNull()!! }
        return Result.failure(last)
    }
}
