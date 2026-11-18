package com.ifpr.androidapptemplatear.ui

import kotlin.random.Random

data class DragonProfile(
    val name: String,
    val rarity: String,
    val element: String,
    val description: String,
    val maxHp: Int,
    val points: Int
)

object SakuraRules {
    const val COMMON = "dragao.glb"
    const val LEGENDARY = "chinese_dragon_-_lunapark.glb"
    const val DAMAGE = 25

    private val common = DragonProfile("Dragão Sakura", "Comum", "Vento",
        "Guardião das pétalas de cerejeira.", 100, 10)
    private val legendary = DragonProfile("Dragão Imperial", "Lendário", "Fogo",
        "Antigo guardião dos Portais Sakura.", 200, 30)

    fun profile(file: String): DragonProfile? = when (file.substringAfterLast("/").lowercase()) {
        COMMON -> common
        LEGENDARY -> legendary
        else -> null
    }

    fun remainingHp(hp: Int): Int = (hp - DAMAGE).coerceAtLeast(0)

    fun encounter(assets: List<String>, legendaryChance: Int, commonChance: Int,
                  random: Random = Random.Default): String? {
        val obstacles = assets.filter { profile(it) == null }
        val roll = random.nextInt(100)
        val chosen = when {
            roll < legendaryChance -> LEGENDARY
            roll < legendaryChance + commonChance -> COMMON
            else -> obstacles.randomOrNull(random) ?: COMMON
        }
        return chosen.takeIf { it in assets } ?: assets.randomOrNull(random)
    }
}
