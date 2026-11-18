package com.ifpr.androidapptemplatear.ui

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Skin(val id: String, val name: String, val price: Int, val color: Int, val weapon: Boolean)

object SakuraItems {
    val all = listOf(
        Skin("dragon_default", "Sakura Original", 0, 0, false),
        Skin("dragon_ice", "Sakura Glacial", 30, 0xFF73C9FF.toInt(), false),
        Skin("dragon_shadow", "Sakura Sombrio", 50, 0xFF713D9E.toInt(), false),
        Skin("dragon_gold", "Sakura Divino", 70, 0xFFFFC44A.toInt(), false),
        Skin("dragon_gift", "Sakura Dourado", -1, 0xFFFFD76B.toInt(), false),
        Skin("sword_default", "Katana Clássica", 0, 0, true),
        Skin("sword_fire", "Katana Infernal", 40, 0xFFFF453A.toInt(), true),
        Skin("sword_gold", "Katana Imperial", -1, 0xFFFFD76B.toInt(), true)
    )
}

class SakuraProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sakura_progress_v1", Context.MODE_PRIVATE)
    private val collection = context.applicationContext.getSharedPreferences("dragon_sakura_collection", Context.MODE_PRIVATE)
    val coins get() = prefs.getInt("coins", 0)
    val record get() = prefs.getInt("record", 0)
    val captures get() = collection.getStringSet("captured_models", emptySet<String>()).orEmpty().size
    val bossUnlocked get() = collection.getStringSet("captured_models", emptySet<String>()).orEmpty().containsAll(listOf(SakuraRules.COMMON, SakuraRules.LEGENDARY))
    val rank get() = when {
        captures >= 3 -> "Mestre Sakura"
        captures >= 2 -> "Caçador de Dragões"
        captures >= 1 -> "Samurai"
        else -> "Aprendiz"
    }
    var playerName: String
        get() = prefs.getString("name", "Samurai") ?: "Samurai"
        set(value) { prefs.edit().putString("name", value.trim().take(24).ifBlank { "Samurai" }).apply() }
    var avatar: String
        get() = prefs.getString("avatar", "🐉") ?: "🐉"
        set(value) { if (value in listOf("🐉", "🌸", "⚔️", "🔥")) prefs.edit().putString("avatar", value).apply() }
    val forgeLevel get() = prefs.getInt("forge", 0).coerceIn(0, 5)
    val damage get() = 25 + forgeLevel * 5
    val forgePrice get() = 30 + forgeLevel * 20
    val redeemed get() = prefs.getBoolean("jailton100", false)
    private fun ownedIds() = prefs.getStringSet("owned", emptySet<String>()).orEmpty().toMutableSet()
    fun owns(skin: Skin) = skin.price == 0 || skin.id in ownedIds()
    fun equipped(weapon: Boolean): Skin {
        val key = if (weapon) "sword" else "dragon"
        val fallback = if (weapon) "sword_default" else "dragon_default"
        val id = prefs.getString(key, fallback)
        return SakuraItems.all.firstOrNull { it.id == id && it.weapon == weapon && owns(it) }
            ?: SakuraItems.all.first { it.id == fallback }
    }
    fun buy(skin: Skin): Boolean {
        if (owns(skin) || skin.price <= 0 || coins < skin.price) return false
        val owned = ownedIds().apply { add(skin.id) }
        return prefs.edit().putInt("coins", coins - skin.price).putStringSet("owned", owned).commit()
    }
    fun equip(skin: Skin): Boolean {
        if (!owns(skin)) return false
        return prefs.edit().putString(if (skin.weapon) "sword" else "dragon", skin.id).commit()
    }
    fun redeem(code: String): String {
        if (!code.trim().equals("jailton100", ignoreCase = true)) return "Código inválido."
        if (redeemed) return "Código já resgatado."
        val owned = ownedIds().apply { add("dragon_gift"); add("sword_gold") }
        return if (prefs.edit().putBoolean("jailton100", true).putStringSet("owned", owned).commit())
            "Sakura Dourado e Katana Imperial desbloqueados!" else "Não foi possível salvar o resgate."
    }
    fun upgrade(): Boolean {
        if (forgeLevel >= 5 || coins < forgePrice) return false
        return prefs.edit().putInt("coins", coins - forgePrice).putInt("forge", forgeLevel + 1).commit()
    }
    fun updateRecord(points: Int) {
        if (points > record) prefs.edit().putInt("record", points).apply()
    }
    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun dailyKey(key: String) = "daily:${day()}:$key"
    fun daily(key: String) = prefs.getInt(dailyKey(key), 0)
    fun event(key: String) {
        val target = when (key) { "attacks" -> 5; "captures" -> 1; "portals" -> 2; else -> return }
        val value = daily(key)
        if (value < target) prefs.edit().putInt(dailyKey(key), value + 1).apply()
    }
    fun portal(name: String) {
        val key = dailyKey("visited")
        val visited = prefs.getStringSet(key, emptySet<String>()).orEmpty().toMutableSet()
        if (visited.add(name)) {
            prefs.edit().putStringSet(key, visited).apply()
            event("portals")
        }
    }
    fun rewardEncounter(points: Int) {
        if (points > 0) prefs.edit().putInt("coins", coins + points).apply()
    }
    val chestClaimed get() = prefs.getBoolean(dailyKey("chest"), false)
    val chestReady get() = daily("attacks") >= 5 && daily("captures") >= 1 && daily("portals") >= 2
    fun claimChest(): Boolean {
        if (!chestReady || chestClaimed) return false
        val owned = ownedIds()
        val bonus = if (owned.add("dragon_shadow")) 50 else 70
        return prefs.edit().putBoolean(dailyKey("chest"), true).putInt("coins", coins + bonus)
            .putStringSet("owned", owned).commit()
    }
}
