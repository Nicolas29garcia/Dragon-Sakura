package com.ifpr.androidapptemplatear.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Gera variantes GLB locais alterando apenas os materiais PBR; preserva geometria e animações.
object SakuraSkinFiles {
    fun modelPath(context: Context, asset: String, color: Int): String {
        if (color == 0) return asset
        val actual = SakuraRules.assetFile(asset)
        return runCatching {
            val dir = File(context.cacheDir, "sakura_skins").apply { mkdirs() }
            val target = File(dir, "${actual.hashCode()}_${color}_v1.glb")
            if (!target.exists()) {
                val bytes = context.assets.open(actual).use { it.readBytes() }
                val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                require(input.int == 0x46546C67 && input.int == 2)
                require(input.int == bytes.size)
                val jsonLength = input.int
                require(input.int == 0x4E4F534A && jsonLength in 1..input.remaining())
                val jsonBytes = ByteArray(jsonLength)
                input.get(jsonBytes)
                val json = JSONObject(String(jsonBytes, Charsets.UTF_8).trimEnd(' ', '\u0000'))
                val materials = json.optJSONArray("materials") ?: JSONArray()
                require(materials.length() > 0)
                val rgba = JSONArray().put(((color shr 16) and 255) / 255.0)
                    .put(((color shr 8) and 255) / 255.0).put((color and 255) / 255.0).put(1.0)
                for (i in 0 until materials.length()) {
                    val material = materials.getJSONObject(i)
                    val pbr = material.optJSONObject("pbrMetallicRoughness") ?: JSONObject()
                    pbr.remove("baseColorTexture")
                    pbr.put("baseColorFactor", rgba)
                    pbr.put("metallicFactor", 0.25)
                    pbr.put("roughnessFactor", 0.48)
                    material.put("pbrMetallicRoughness", pbr)
                    material.remove("extensions")
                }
                val raw = json.toString().toByteArray(Charsets.UTF_8)
                val padded = (raw.size + 3) and -4
                val tail = input.remaining()
                val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(0x46546C67).putInt(2).putInt(20 + padded + tail)
                    .putInt(padded).putInt(0x4E4F534A).array()
                val temp = File.createTempFile("skin_", ".tmp", dir)
                try {
                    temp.outputStream().use { stream ->
                        stream.write(header)
                        stream.write(raw)
                        repeat(padded - raw.size) { stream.write(32) }
                        stream.write(bytes, input.position(), tail)
                    }
                    check(temp.renameTo(target) || target.exists())
                } finally { temp.delete() }
            }
            android.net.Uri.fromFile(target).toString()
        }.getOrDefault(actual)
    }
}
