package org.ivanivorontsov.fotomaster.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.ivanivorontsov.fotomaster.core.Adjustments
import org.ivanivorontsov.fotomaster.core.Doc
import org.ivanivorontsov.fotomaster.core.DocJson
import org.ivanivorontsov.fotomaster.core.Preset
import java.io.File

data class ProjectInfo(val id: String, val dir: File, val modified: Long) {
    val thumb get() = File(dir, "thumb.jpg")
    val source get() = File(dir, "source")
}

object Projects {
    fun root(ctx: Context) = File(ctx.filesDir, "projects").apply { mkdirs() }

    fun create(ctx: Context, uri: Uri): ProjectInfo {
        val id = System.currentTimeMillis().toString()
        val dir = File(root(ctx), id).apply { mkdirs() }
        ctx.contentResolver.openInputStream(uri)!!.use { inp -> File(dir, "source").outputStream().use { inp.copyTo(it) } }
        return ProjectInfo(id, dir, System.currentTimeMillis())
    }

    fun list(ctx: Context): List<ProjectInfo> = root(ctx).listFiles()?.filter { File(it, "source").exists() }
        ?.map { ProjectInfo(it.name, it, File(it, "doc.json").takeIf { f -> f.exists() }?.lastModified() ?: it.lastModified()) }
        ?.sortedByDescending { it.modified } ?: emptyList()

    fun saveDoc(p: ProjectInfo, doc: Doc, thumb: Bitmap?) {
        File(p.dir, "doc.json").writeText(DocJson.doc(doc))
        thumb?.let { b -> File(p.dir, "thumb.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
    }

    fun loadDoc(p: ProjectInfo): Doc = File(p.dir, "doc.json").takeIf { it.exists() }?.let { runCatching { DocJson.doc(it.readText()) }.getOrNull() } ?: Doc()

    fun delete(p: ProjectInfo) { p.dir.deleteRecursively() }

    fun thumb(p: ProjectInfo): Bitmap? = p.thumb.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun userPresets(ctx: Context): List<Preset> {
        val f = File(ctx.filesDir, "presets.json"); if (!f.exists()) return emptyList()
        return runCatching { val a = JSONArray(f.readText()); (0 until a.length()).map { val o = a.getJSONObject(it); Preset(o.getString("name"), DocJson.adj(o.getJSONObject("adj")), false) } }.getOrDefault(emptyList())
    }

    fun saveUserPresets(ctx: Context, list: List<Preset>) {
        File(ctx.filesDir, "presets.json").writeText(JSONArray(list.map { JSONObject().put("name", it.name).put("adj", DocJson.adj(it.adj)) }).toString())
    }

    fun addPreset(ctx: Context, name: String, adj: Adjustments) = saveUserPresets(ctx, userPresets(ctx).filter { it.name != name } + Preset(name, adj, false))
}
