package com.ifpr.androidapptemplatear

import com.ifpr.androidapptemplatear.ui.SakuraRules
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SakuraRulesTest {
    @Test fun batalha() {
        assertEquals(0, SakuraRules.remainingHp(25))
        assertEquals(0, SakuraRules.remainingHp(0))
        assertEquals(75, SakuraRules.remainingHp(100))
        assertEquals(200, SakuraRules.profile(SakuraRules.LEGENDARY)?.maxHp)
        assertEquals(30, SakuraRules.profile(SakuraRules.LEGENDARY)?.points)
        assertEquals(10, SakuraRules.profile(SakuraRules.COMMON)?.points)
        assertNull(SakuraRules.profile("sofa.glb"))
    }

    @Test fun portais() {
        val files = listOf(SakuraRules.COMMON, SakuraRules.LEGENDARY, "sofa.glb")
        val random = Random(42)
        repeat(100) { assertNotEquals(SakuraRules.LEGENDARY, SakuraRules.encounter(files, 0, 70, random)) }
        repeat(100) { assertEquals(SakuraRules.LEGENDARY, SakuraRules.encounter(files, 100, 0, random)) }
        assertEquals(SakuraRules.COMMON, SakuraRules.encounter(listOf(SakuraRules.COMMON), 100, 0, random))
        assertNull(SakuraRules.encounter(emptyList(), 10, 60, random))
    }
}
