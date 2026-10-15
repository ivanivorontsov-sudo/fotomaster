package org.ivanivorontsov.fotomaster.ai

object ResourceFilter {
    private val hosts = Regex("(^|\\.)(google-analytics\\.com|googletagmanager\\.com|doubleclick\\.net|mc\\.yandex\\.(ru|com)|yandexmetrica\\.com|an\\.yandex\\.ru|hotjar\\.com|sentry\\.io|sentry-cdn\\.com|clarity\\.ms|segment\\.(io|com)|mixpanel\\.com|amplitude\\.com|facebook\\.net|connect\\.facebook\\.net|tiktok\\.com|arms-retcode\\.aliyuncs\\.com|retcode\\.alicdn\\.com|log\\.mmstat\\.com|g\\.alicdn\\.com/alilog|top-fox\\.ru|top-fwz1\\.mail\\.ru|vk\\.com/rtrg)$")
    private val paths = Regex("(/gtag/js|/analytics\\.js|/watch/\\d+|/metrika/|/tag\\.js|/collect\\?|/beacon|/aplus[^/]*\\.js|/arms[^/]*\\.js)", RegexOption.IGNORE_CASE)
    private val media = Regex("\\.(mp4|webm|m3u8|mov)(\\?|$)", RegexOption.IGNORE_CASE)

    fun block(url: String, mainFrame: Boolean): Boolean {
        if (mainFrame || !url.startsWith("http")) return false
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return hosts.containsMatchIn(host) || paths.containsMatchIn(url) || media.containsMatchIn(url)
    }
}
