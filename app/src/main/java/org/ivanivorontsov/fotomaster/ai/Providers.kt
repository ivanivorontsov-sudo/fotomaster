package org.ivanivorontsov.fotomaster.ai

import android.content.Context
import org.json.JSONObject

data class AiProvider(val id: String, val title: String, val startUrl: String, val hint: String, val terms: String)

object Providers {
    fun load(ctx: Context): List<AiProvider> = parse(ctx.assets.open("ai/providers.json").bufferedReader().readText())

    fun parse(text: String): List<AiProvider> {
        val arr = JSONObject(text).getJSONArray("providers")
        return (0 until arr.length()).map { val o = arr.getJSONObject(it)
            AiProvider(o.getString("id"), o.getString("title"), o.getString("startUrl"), o.getString("hint"), o.optString("terms")) }
    }
}
