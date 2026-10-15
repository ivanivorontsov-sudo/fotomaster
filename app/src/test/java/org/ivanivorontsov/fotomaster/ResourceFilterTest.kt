package org.ivanivorontsov.fotomaster

import org.ivanivorontsov.fotomaster.ai.ResourceFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceFilterTest {
    @Test fun blocksTrackersAndVideoButNotSiteOrImagesOrFonts() {
        listOf("https://www.googletagmanager.com/gtag/js?id=G-1", "https://mc.yandex.ru/metrika/tag.js", "https://www.google-analytics.com/g/collect?v=2",
            "https://arms-retcode.aliyuncs.com/r.png", "https://cdn.qwen.ai/banner/intro.mp4", "https://o1.ingest.sentry.io/api/1/envelope/").forEach { assertTrue(it, ResourceFilter.block(it, false)) }
        listOf("https://chat.qwen.ai/", "https://chat.qwen.ai/api/v2/chats/new", "https://cdn.qwenlm.ai/output/abc.png", "https://avatars.mds.yandex.net/get-shedevrum/1/abc/orig",
            "https://yastatic.net/s3/alice/fonts/YSText.woff2", "https://alice.yandex.ru/", "https://giga.chat/api/attachments").forEach { assertFalse(it, ResourceFilter.block(it, false)) }
        assertFalse(ResourceFilter.block("https://mc.yandex.ru/metrika/tag.js", true))
    }
}
