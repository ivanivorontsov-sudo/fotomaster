package org.ivanivorontsov.fotomaster

import org.ivanivorontsov.fotomaster.ai.MemoryKeyValue
import org.ivanivorontsov.fotomaster.ai.PromptSession
import org.junit.Assert.*
import org.junit.Test

class PromptSessionTest {
    private val ids = listOf("qwen", "alice", "giga")

    @Test fun rememberLastProviderAndPrompt() {
        val kv = MemoryKeyValue()
        val s = PromptSession<String>(kv, ids)
        assertEquals("qwen", s.providerId)
        s.selectProvider("giga"); s.updatePrompt("сделай ярче")
        val again = PromptSession<String>(kv, ids)
        assertEquals("giga", again.providerId); assertEquals("сделай ярче", again.prompt)
        kv.put(PromptSession.KEY, "unknown"); assertEquals("qwen", PromptSession<String>(kv, ids).providerId)
    }

    @Test fun quickChipsCoverRequiredPrompts() {
        val titles = PromptSession.QUICK.map { it.first.lowercase() }
        listOf("улучши качество", "удали фон", "аниме", "убери лишних людей").forEach { w -> assertTrue(w, titles.any { it.contains(w) }) }
        val s = PromptSession<String>(MemoryKeyValue(), ids)
        s.applyChip(1); assertTrue(s.prompt.contains("фон"))
    }

    @Test fun resultsHistoryNewestFirstSelectedAndRetry() {
        val s = PromptSession<String>(MemoryKeyValue(), ids)
        s.updatePrompt("первый"); s.startRequest(); val a = s.addResult("Qwen", "img1", 10, 10)
        s.selectProvider("alice"); s.updatePrompt("второй"); s.startRequest(); val b = s.addResult("Алиса AI", "img2", 10, 10)
        assertEquals(listOf(b.id, a.id), s.results.map { it.id }); assertEquals(b.id, s.selectedId)
        assertEquals("alice" to "второй", s.retryRequest())
        s.select(a.id); assertEquals("первый", s.selected!!.prompt); assertEquals("qwen", s.selected!!.providerId)
        s.markApplied(a.id); s.markSaved(b.id)
        assertTrue(s.results.first { it.id == a.id }.applied); assertTrue(s.results.first { it.id == b.id }.saved)
    }

    @Test fun historyIsCapped() {
        val s = PromptSession<Int>(MemoryKeyValue(), ids)
        repeat(PromptSession.MAX + 5) { s.startRequest(); s.addResult("Qwen", it, 1, 1) }
        assertEquals(PromptSession.MAX, s.results.size); assertEquals(PromptSession.MAX + 4, s.results.first().image)
    }

    @Test fun blankPromptCannotGenerate() {
        val s = PromptSession<String>(MemoryKeyValue(), ids); s.updatePrompt("   "); assertFalse(s.canGenerate())
    }
}
