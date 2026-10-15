package org.ivanivorontsov.fotomaster.core

import org.json.JSONArray
import org.json.JSONObject

object DocJson {
    fun adj(a: Adjustments) = JSONObject().apply {
        put("exposure", a.exposure); put("contrast", a.contrast); put("highlights", a.highlights); put("shadows", a.shadows)
        put("whites", a.whites); put("blacks", a.blacks); put("temperature", a.temperature); put("tint", a.tint)
        put("saturation", a.saturation); put("vibrance", a.vibrance); put("clarity", a.clarity); put("sharpen", a.sharpen)
        put("denoise", a.denoise); put("vignette", a.vignette)
        put("levels", JSONArray(listOf(a.levels.inBlack, a.levels.inWhite, a.levels.gamma.toDouble(), a.levels.outBlack, a.levels.outWhite)))
        put("curve", JSONArray(a.curve.flatMap { listOf(it.first.toDouble(), it.second.toDouble()) }))
        put("hsl", JSONArray(a.hsl.map { it.toDouble() }))
    }

    fun adj(o: JSONObject): Adjustments {
        fun f(k: String) = o.optDouble(k, 0.0).toFloat()
        val lv = o.optJSONArray("levels")
        val cv = o.optJSONArray("curve")
        val hs = o.optJSONArray("hsl")
        return Adjustments(f("exposure"), f("contrast"), f("highlights"), f("shadows"), f("whites"), f("blacks"), f("temperature"), f("tint"),
            f("saturation"), f("vibrance"), f("clarity"), f("sharpen"), f("denoise"), f("vignette"),
            if (lv != null && lv.length() == 5) Levels(lv.getInt(0), lv.getInt(1), lv.getDouble(2).toFloat(), lv.getInt(3), lv.getInt(4)) else Levels(),
            if (cv != null && cv.length() >= 4) (0 until cv.length() / 2).map { cv.getDouble(it * 2).toFloat() to cv.getDouble(it * 2 + 1).toFloat() } else Adjustments.IDENTITY_CURVE,
            if (hs != null && hs.length() == 24) (0 until 24).map { hs.getDouble(it).toFloat() } else List(24) { 0f })
    }

    private fun stroke(s: Stroke) = JSONObject().apply {
        put("p", JSONArray(s.points.flatMap { listOf(it.x.toDouble(), it.y.toDouble()) })); put("r", s.radius.toDouble()); put("c", s.color)
        put("h", s.hardness.toDouble()); put("e", s.erase)
    }
    private fun stroke(o: JSONObject): Stroke {
        val p = o.getJSONArray("p")
        return Stroke((0 until p.length() / 2).map { PointN(p.getDouble(it * 2).toFloat(), p.getDouble(it * 2 + 1).toFloat()) },
            o.getDouble("r").toFloat(), o.getInt("c"), o.getDouble("h").toFloat(), o.getBoolean("e"))
    }
    private fun strokes(l: List<Stroke>) = JSONArray(l.map { stroke(it) })
    private fun strokes(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { stroke(a.getJSONObject(it)) }

    fun layer(l: Layer): JSONObject = JSONObject().apply {
        put("id", l.id); put("name", l.name); put("visible", l.visible); put("opacity", l.opacity.toDouble())
        when (l) {
            is Layer.Paint -> { put("type", "paint"); put("blend", l.blend.name); put("strokes", strokes(l.strokes)) }
            is Layer.Text -> { put("type", "text"); put("text", l.text); put("x", l.x.toDouble()); put("y", l.y.toDouble()); put("size", l.size.toDouble()); put("color", l.color); put("shadow", l.shadow); put("bold", l.bold) }
            is Layer.Retouch -> { put("type", "retouch"); put("ops", JSONArray(l.ops.map { op -> when (op) {
                is RetouchOp.Heal -> JSONObject().put("k", "heal").put("x", op.x.toDouble()).put("y", op.y.toDouble()).put("r", op.r.toDouble())
                is RetouchOp.Clone -> JSONObject().put("k", "clone").put("dx", op.dx.toDouble()).put("dy", op.dy.toDouble()).put("s", stroke(op.stroke))
            } })) }
            is Layer.Local -> { put("type", "local"); put("shape", l.shape.name); put("strokes", strokes(l.strokes)); put("cx", l.cx.toDouble()); put("cy", l.cy.toDouble())
                put("radius", l.radius.toDouble()); put("feather", l.feather.toDouble()); put("angle", l.angle.toDouble()); put("invert", l.invert); put("maskRef", l.maskRef ?: ""); put("adj", adj(l.adj)) }
            is Layer.Cutout -> { put("type", "cutout"); put("maskRef", l.maskRef); put("mode", l.mode.name); put("color", l.color); put("feather", l.feather.toDouble()); put("fix", strokes(l.fixStrokes)) }
            is Layer.Picture -> { put("type", "picture"); put("imageRef", l.imageRef); put("blend", l.blend.name); put("erase", strokes(l.eraseStrokes)) }
        }
    }

    fun layer(o: JSONObject): Layer {
        val id = o.getLong("id"); val name = o.getString("name"); val vis = o.getBoolean("visible"); val op = o.getDouble("opacity").toFloat()
        fun d(k: String) = o.getDouble(k).toFloat()
        return when (o.getString("type")) {
            "paint" -> Layer.Paint(id, name, vis, op, Blend.valueOf(o.getString("blend")), strokes(o.optJSONArray("strokes")))
            "text" -> Layer.Text(id, name, vis, op, o.getString("text"), d("x"), d("y"), d("size"), o.getInt("color"), o.getBoolean("shadow"), o.getBoolean("bold"))
            "retouch" -> { val a = o.getJSONArray("ops"); Layer.Retouch(id, name, vis, op, (0 until a.length()).map { val j = a.getJSONObject(it)
                if (j.getString("k") == "heal") RetouchOp.Heal(j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), j.getDouble("r").toFloat())
                else RetouchOp.Clone(j.getDouble("dx").toFloat(), j.getDouble("dy").toFloat(), stroke(j.getJSONObject("s"))) }) }
            "local" -> Layer.Local(id, name, vis, op, MaskShape.valueOf(o.getString("shape")), strokes(o.optJSONArray("strokes")), d("cx"), d("cy"), d("radius"), d("feather"), d("angle"),
                o.getBoolean("invert"), o.optString("maskRef").ifEmpty { null }, adj(o.getJSONObject("adj")))
            "cutout" -> Layer.Cutout(id, name, vis, op, o.getString("maskRef"), BgMode.valueOf(o.getString("mode")), o.getInt("color"), d("feather"), strokes(o.optJSONArray("fix")))
            else -> Layer.Picture(id, name, vis, op, o.getString("imageRef"), Blend.valueOf(o.getString("blend")), strokes(o.optJSONArray("erase")))
        }
    }

    fun doc(d: Doc): String = JSONObject().apply {
        val g = d.geometry
        put("geometry", JSONObject().put("crop", JSONArray(listOf(g.crop.l, g.crop.t, g.crop.r, g.crop.b).map { it.toDouble() })).put("rot90", g.rot90).put("flipH", g.flipH).put("straighten", g.straighten.toDouble()))
        put("adj", adj(d.adj)); put("layers", JSONArray(d.layers.map { layer(it) }))
    }.toString()

    fun doc(s: String): Doc {
        val o = JSONObject(s); val g = o.getJSONObject("geometry"); val c = g.getJSONArray("crop")
        val ls = o.getJSONArray("layers")
        return Doc(Geometry(RectN(c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), c.getDouble(2).toFloat(), c.getDouble(3).toFloat()), g.getInt("rot90"), g.getBoolean("flipH"), g.getDouble("straighten").toFloat()),
            adj(o.getJSONObject("adj")), (0 until ls.length()).map { layer(ls.getJSONObject(it)) })
    }
}
