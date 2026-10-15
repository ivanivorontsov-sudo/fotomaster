package org.ivanivorontsov.fotomaster.ai

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AiProvider(val id: String, val title: String, val loginUrl: String, val startUrl: String, val returnsImage: String, val raw: JSONObject)

object Providers {
    fun load(ctx: Context): List<AiProvider> {
        val override = File(ctx.filesDir, "ai/providers.json")
        val text = if (override.exists()) override.readText() else ctx.assets.open("ai/providers.json").bufferedReader().readText()
        return parse(text)
    }

    fun parse(text: String): List<AiProvider> {
        val arr = JSONObject(text).getJSONArray("providers")
        return (0 until arr.length()).map { val o = arr.getJSONObject(it)
            AiProvider(o.getString("id"), o.getString("title"), o.getString("loginUrl"), o.getString("startUrl"), o.getString("returnsImage"), o) }
    }

    val QUICK_PROMPTS = listOf(
        "Улучши фото: чёткость, свет и цвета, ничего не добавляй и не меняй лица",
        "Удали фон, оставь только главный объект на белом фоне",
        "Убери лишних людей и предметы на заднем плане",
        "Восстанови старое фото: убери царапины, верни чёткость и цвет",
        "Сделай профессиональную портретную ретушь, естественно",
        "Замени небо на красивый закат",
    )
}
