package org.ivanivorontsov.fotomaster.core

data class Preset(val name: String, val adj: Adjustments, val builtIn: Boolean = true)

object Presets {
    private fun hsl(vararg kv: Pair<Int, Triple<Float, Float, Float>>): List<Float> {
        val l = MutableList(24) { 0f }
        for ((k, v) in kv) { l[k * 3] = v.first; l[k * 3 + 1] = v.second; l[k * 3 + 2] = v.third }
        return l
    }
    val builtIn = listOf(
        Preset("Естественный", Adjustments(contrast = 0.06f, vibrance = 0.15f, clarity = 0.1f, sharpen = 0.25f)),
        Preset("Яркий", Adjustments(contrast = 0.15f, saturation = 0.2f, vibrance = 0.25f, clarity = 0.2f)),
        Preset("Тёплый вечер", Adjustments(temperature = 0.3f, tint = 0.05f, shadows = 0.2f, highlights = -0.2f, vibrance = 0.1f)),
        Preset("Холодное утро", Adjustments(temperature = -0.25f, exposure = 0.1f, contrast = -0.05f)),
        Preset("Плёнка", Adjustments(contrast = -0.08f, saturation = -0.15f, curve = listOf(0f to 0.07f, 0.25f to 0.24f, 0.75f to 0.8f, 1f to 0.95f), temperature = 0.08f, vignette = 0.2f)),
        Preset("Ч/б контраст", Adjustments(saturation = -1f, contrast = 0.25f, clarity = 0.25f, curve = listOf(0f to 0f, 0.25f to 0.2f, 0.75f to 0.82f, 1f to 1f))),
        Preset("Ч/б мягкий", Adjustments(saturation = -1f, contrast = -0.05f, shadows = 0.2f)),
        Preset("Портрет", Adjustments(exposure = 0.1f, clarity = -0.12f, vibrance = 0.08f, temperature = 0.06f, denoise = 0.25f, hsl = hsl(1 to Triple(0f, -0.1f, 0.15f)))),
        Preset("Пейзаж", Adjustments(clarity = 0.3f, vibrance = 0.3f, highlights = -0.3f, shadows = 0.2f, hsl = hsl(5 to Triple(0f, 0.2f, -0.2f), 3 to Triple(0f, 0.15f, 0f)))),
        Preset("Еда", Adjustments(exposure = 0.12f, temperature = 0.12f, saturation = 0.12f, clarity = 0.15f, sharpen = 0.3f)),
        Preset("Ночь", Adjustments(denoise = 0.5f, shadows = 0.3f, highlights = -0.25f, temperature = -0.08f, sharpen = 0.15f)),
        Preset("Тиль и оранж", Adjustments(hsl = hsl(1 to Triple(0f, 0.2f, 0.05f), 4 to Triple(0.3f, 0.1f, -0.1f), 5 to Triple(-0.3f, 0f, -0.1f)), contrast = 0.1f)),
    )
}
